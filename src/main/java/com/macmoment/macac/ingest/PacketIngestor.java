package com.macmoment.macac.ingest;

import com.macmoment.macac.model.TelemetryInput;

import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

/**
 * Interface for packet/event ingestion.
 * Implementations capture player movement data from various sources.
 */
public interface PacketIngestor {
    
    /**
     * Starts capturing packets/events.
     */
    void start();
    
    /**
     * Stops capturing packets/events.
     */
    void stop();
    
    /**
     * Returns whether this ingestor is currently active.
     * 
     * @return true if active
     */
    boolean isActive();
    
    /**
     * Returns the name of this ingestor for logging.
     * 
     * @return Ingestor name
     */
    String getName();
    
    /**
     * Sets the callback for received telemetry.
     * 
     * @param callback Callback that receives player and telemetry
     */
    void setCallback(BiConsumer<Player, TelemetryInput> callback);
}
