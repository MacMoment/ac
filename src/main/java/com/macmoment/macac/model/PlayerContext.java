package com.macmoment.macac.model;

import com.macmoment.macac.util.RingBuffer;
import com.macmoment.macac.util.Stats;

import java.util.UUID;

/**
 * Per-player context containing state, history, and statistics.
 * This class is thread-safe for single-writer operations.
 */
public final class PlayerContext {
    private final UUID playerId;
    private final String playerName;
    
    // History buffers
    private final RingBuffer<TelemetryInput> telemetryHistory;
    private final RingBuffer<Features> featureHistory;
    private final Stats.RollingWindow pingWindow;
    private final Stats.RollingWindow packetDeltaWindow;
    
    // EWMA trackers
    private final Stats.EWMA pingEwma;
    private final Stats.EWMA speedEwma;
    private final Stats.EWMA accelEwma;
    
    // State tracking
    private volatile long lastTelemetryNanos;
    private volatile long lastAlertNanos;
    private volatile long exemptUntilNanos;
    private volatile long cooldownUntilNanos;
    private volatile int totalViolations;
    private volatile int recentViolations;
    
    // Exemption flags
    private volatile boolean teleporting;
    private volatile boolean worldChanging;
    private volatile boolean recentJoin;

    /**
     * Creates a new player context.
     * 
     * @param playerId Player UUID
     * @param playerName Player name
     * @param historySize Size of history buffers
     * @param pingWindowSize Size of ping rolling window
     * @param ewmaAlpha EWMA smoothing factor
     */
    public PlayerContext(UUID playerId, String playerName, int historySize, 
                        int pingWindowSize, double ewmaAlpha) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.telemetryHistory = new RingBuffer<>(historySize);
        this.featureHistory = new RingBuffer<>(historySize);
        this.pingWindow = new Stats.RollingWindow(pingWindowSize);
        this.packetDeltaWindow = new Stats.RollingWindow(pingWindowSize);
        this.pingEwma = new Stats.EWMA(ewmaAlpha);
        this.speedEwma = new Stats.EWMA(ewmaAlpha);
        this.accelEwma = new Stats.EWMA(ewmaAlpha);
        this.lastTelemetryNanos = 0;
        this.lastAlertNanos = 0;
        this.exemptUntilNanos = 0;
        this.cooldownUntilNanos = 0;
        this.totalViolations = 0;
        this.recentViolations = 0;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    
    // History access
    public RingBuffer<TelemetryInput> getTelemetryHistory() { return telemetryHistory; }
    public RingBuffer<Features> getFeatureHistory() { return featureHistory; }
    public Stats.RollingWindow getPingWindow() { return pingWindow; }
    public Stats.RollingWindow getPacketDeltaWindow() { return packetDeltaWindow; }
    
    // EWMA access
    public Stats.EWMA getPingEwma() { return pingEwma; }
    public Stats.EWMA getSpeedEwma() { return speedEwma; }
    public Stats.EWMA getAccelEwma() { return accelEwma; }
    
    // Timing
    public long getLastTelemetryNanos() { return lastTelemetryNanos; }
    public void setLastTelemetryNanos(long nanos) { this.lastTelemetryNanos = nanos; }
    
    public long getLastAlertNanos() { return lastAlertNanos; }
    public void setLastAlertNanos(long nanos) { this.lastAlertNanos = nanos; }
    
    // Exemptions
    public long getExemptUntilNanos() { return exemptUntilNanos; }
    public void setExemptUntilNanos(long nanos) { this.exemptUntilNanos = nanos; }
    
    public long getCooldownUntilNanos() { return cooldownUntilNanos; }
    public void setCooldownUntilNanos(long nanos) { this.cooldownUntilNanos = nanos; }
    
    public boolean isTeleporting() { return teleporting; }
    public void setTeleporting(boolean teleporting) { this.teleporting = teleporting; }
    
    public boolean isWorldChanging() { return worldChanging; }
    public void setWorldChanging(boolean worldChanging) { this.worldChanging = worldChanging; }
    
    public boolean isRecentJoin() { return recentJoin; }
    public void setRecentJoin(boolean recentJoin) { this.recentJoin = recentJoin; }
    
    // Violations
    public int getTotalViolations() { return totalViolations; }
    public void incrementViolations() { 
        this.totalViolations++; 
        this.recentViolations++;
    }
    
    public int getRecentViolations() { return recentViolations; }
    public void resetRecentViolations() { this.recentViolations = 0; }
    
    /**
     * Checks if the player is currently exempt from checks.
     * 
     * @param currentNanos Current monotonic time
     * @return true if exempt
     */
    public boolean isExempt(long currentNanos) {
        return currentNanos < exemptUntilNanos || 
               teleporting || worldChanging || recentJoin;
    }
    
    /**
     * Checks if alerts are on cooldown.
     * 
     * @param currentNanos Current monotonic time
     * @return true if on cooldown
     */
    public boolean isOnCooldown(long currentNanos) {
        return currentNanos < cooldownUntilNanos;
    }
    
    /**
     * Adds telemetry to history and updates EWMA.
     * 
     * @param input Telemetry input
     */
    public void addTelemetry(TelemetryInput input) {
        telemetryHistory.push(input);
        pingWindow.add(input.ping());
        pingEwma.update(input.ping());
        
        if (lastTelemetryNanos > 0 && input.nanoTime() > lastTelemetryNanos) {
            long deltaNanos = input.nanoTime() - lastTelemetryNanos;
            packetDeltaWindow.add(deltaNanos / 1_000_000.0); // Convert to ms
        }
        lastTelemetryNanos = input.nanoTime();
    }
    
    /**
     * Adds features to history and updates EWMA.
     * 
     * @param features Extracted features
     */
    public void addFeatures(Features features) {
        featureHistory.push(features);
        speedEwma.update(features.horizSpeed());
        accelEwma.update(features.horizAccel());
    }
    
    /**
     * Gets the median ping from the rolling window.
     * 
     * @return Median ping in ms
     */
    public double getMedianPing() {
        return pingWindow.median();
    }
    
    /**
     * Gets the MAD of ping values.
     * 
     * @return Ping MAD
     */
    public double getPingMad() {
        return pingWindow.mad();
    }
    
    /**
     * Clears all history and resets state.
     */
    public void reset() {
        telemetryHistory.clear();
        featureHistory.clear();
        pingWindow.clear();
        packetDeltaWindow.clear();
        pingEwma.reset();
        speedEwma.reset();
        accelEwma.reset();
        lastTelemetryNanos = 0;
        totalViolations = 0;
        recentViolations = 0;
        teleporting = false;
        worldChanging = false;
        recentJoin = false;
    }
}
