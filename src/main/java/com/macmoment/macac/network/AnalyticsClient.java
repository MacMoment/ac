package com.macmoment.macac.network;

import com.macmoment.macac.model.Violation;
import com.macmoment.macac.util.NativeHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Client for sending violation data to a centralized analytics server.
 * Supports both native (high-performance) and Java socket implementations.
 * 
 * Features:
 * - Async non-blocking sends via queue
 * - Auto-reconnection on failure
 * - Graceful degradation if native not available
 */
public final class AnalyticsClient {
    
    private static final Logger LOGGER = Logger.getLogger(AnalyticsClient.class.getName());
    
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int reconnectDelayMs;
    
    // Connection state
    private final AtomicBoolean running;
    private final AtomicBoolean connected;
    private final BlockingQueue<Violation> sendQueue;
    
    // Native connection (if available)
    private long nativeHandle;
    
    // Java fallback connection
    private Socket socket;
    private BufferedWriter writer;
    
    // Sender thread
    private Thread senderThread;
    
    /**
     * Creates a new analytics client.
     * 
     * @param host Server hostname
     * @param port Server port
     * @param connectTimeoutMs Connection timeout in milliseconds
     * @param reconnectDelayMs Delay between reconnection attempts
     */
    public AnalyticsClient(String host, int port, int connectTimeoutMs, int reconnectDelayMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.reconnectDelayMs = reconnectDelayMs;
        this.running = new AtomicBoolean(false);
        this.connected = new AtomicBoolean(false);
        this.sendQueue = new LinkedBlockingQueue<>(1000); // Max 1000 queued
        this.nativeHandle = 0;
    }
    
    /**
     * Starts the analytics client.
     * Spawns a background thread for sending violations.
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        
        running.set(true);
        
        senderThread = new Thread(this::senderLoop, "MacAC-Analytics-Sender");
        senderThread.setDaemon(true);
        senderThread.start();
        
        LOGGER.info("Analytics client started for " + host + ":" + port);
    }
    
    /**
     * Stops the analytics client.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        // Interrupt sender thread
        if (senderThread != null) {
            senderThread.interrupt();
            try {
                senderThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        disconnect();
        sendQueue.clear();
        
        LOGGER.info("Analytics client stopped");
    }
    
    /**
     * Queues a violation for sending.
     * Non-blocking - returns immediately.
     * 
     * @param violation Violation to send
     * @return true if queued successfully
     */
    public boolean sendViolation(Violation violation) {
        if (!running.get() || violation == null) {
            return false;
        }
        
        return sendQueue.offer(violation);
    }
    
    /**
     * Returns the current queue size.
     * 
     * @return Number of pending violations
     */
    public int getQueueSize() {
        return sendQueue.size();
    }
    
    /**
     * Returns true if connected to server.
     * 
     * @return Connection status
     */
    public boolean isConnected() {
        return connected.get();
    }
    
    /**
     * Background sender loop.
     */
    private void senderLoop() {
        while (running.get()) {
            try {
                // Ensure connection
                if (!connected.get()) {
                    connect();
                    if (!connected.get()) {
                        Thread.sleep(reconnectDelayMs);
                        continue;
                    }
                }
                
                // Take from queue (blocking with timeout)
                Violation violation = sendQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (violation == null) {
                    continue;
                }
                
                // Send violation
                boolean sent = doSend(violation);
                if (!sent) {
                    // Put back in queue if send failed
                    sendQueue.offer(violation);
                    disconnect();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.warning("Error in sender loop: " + e.getMessage());
                disconnect();
            }
        }
    }
    
    /**
     * Establishes connection to server.
     */
    private void connect() {
        // Try native connection first
        if (NativeHelper.isNativeAvailable()) {
            try {
                nativeHandle = NativeHelper.netConnect(host, port);
                if (nativeHandle != 0) {
                    connected.set(true);
                    LOGGER.info("Connected to analytics server (native): " + host + ":" + port);
                    return;
                }
            } catch (Exception e) {
                LOGGER.fine("Native connection failed: " + e.getMessage());
            }
        }
        
        // Java fallback
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(5000);
            
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            
            connected.set(true);
            LOGGER.info("Connected to analytics server (Java): " + host + ":" + port);
            
        } catch (IOException e) {
            LOGGER.fine("Java connection failed: " + e.getMessage());
            disconnect();
        }
    }
    
    /**
     * Closes the connection.
     */
    private void disconnect() {
        connected.set(false);
        
        // Close native connection
        if (nativeHandle != 0) {
            try {
                NativeHelper.netClose(nativeHandle);
            } catch (Exception e) {
                // Ignore
            }
            nativeHandle = 0;
        }
        
        // Close Java connection
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore
            }
            writer = null;
        }
        
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            socket = null;
        }
    }
    
    /**
     * Sends a violation using available connection.
     * 
     * @param violation Violation to send
     * @return true if sent successfully
     */
    private boolean doSend(Violation violation) {
        // Try native first
        if (nativeHandle != 0) {
            try {
                int result = NativeHelper.netSendViolation(
                    nativeHandle,
                    violation.playerId().toString(),
                    violation.category(),
                    violation.confidence(),
                    violation.severity(),
                    violation.timestamp()
                );
                return result >= 0;
            } catch (Exception e) {
                LOGGER.fine("Native send failed: " + e.getMessage());
                nativeHandle = 0;
                return false;
            }
        }
        
        // Java fallback
        if (writer != null) {
            try {
                String json = formatJson(violation);
                writer.write(json);
                writer.newLine();
                writer.flush();
                return true;
            } catch (IOException e) {
                LOGGER.fine("Java send failed: " + e.getMessage());
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Formats a violation as JSON.
     */
    private String formatJson(Violation violation) {
        return String.format(
            "{\"type\":\"violation\",\"player_uuid\":\"%s\",\"player_name\":\"%s\"," +
            "\"category\":\"%s\",\"confidence\":%.6f,\"severity\":%.6f,\"timestamp\":%d}",
            violation.playerId(),
            violation.playerName(),
            violation.category(),
            violation.confidence(),
            violation.severity(),
            violation.timestamp()
        );
    }
}
