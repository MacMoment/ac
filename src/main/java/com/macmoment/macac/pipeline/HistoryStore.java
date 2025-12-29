package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.PlayerContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player history and context data.
 * Thread-safe for concurrent access from multiple threads.
 */
public final class HistoryStore {
    
    private final Map<UUID, PlayerContext> contexts;
    private int historySize;
    private int medianWindowSize;
    private double ewmaAlpha;
    
    public HistoryStore() {
        this.contexts = new ConcurrentHashMap<>();
        this.historySize = 64;
        this.medianWindowSize = 20;
        this.ewmaAlpha = 0.3;
    }
    
    /**
     * Updates configuration values.
     * 
     * @param config Engine configuration
     */
    public void configure(EngineConfig config) {
        this.historySize = config.getHistorySize();
        this.medianWindowSize = config.getMedianWindowSize();
        this.ewmaAlpha = config.getEwmaAlpha();
    }
    
    /**
     * Gets or creates a player context.
     * 
     * @param playerId Player UUID
     * @param playerName Player name
     * @return Player context
     */
    public PlayerContext getOrCreate(UUID playerId, String playerName) {
        return contexts.computeIfAbsent(playerId, 
            id -> new PlayerContext(id, playerName, historySize, medianWindowSize, ewmaAlpha));
    }
    
    /**
     * Gets a player context if it exists.
     * 
     * @param playerId Player UUID
     * @return Player context or null
     */
    public PlayerContext get(UUID playerId) {
        return contexts.get(playerId);
    }
    
    /**
     * Removes a player context.
     * 
     * @param playerId Player UUID
     * @return Removed context or null
     */
    public PlayerContext remove(UUID playerId) {
        return contexts.remove(playerId);
    }
    
    /**
     * Clears all stored contexts.
     */
    public void clear() {
        contexts.clear();
    }
    
    /**
     * Returns the number of tracked players.
     * 
     * @return Player count
     */
    public int size() {
        return contexts.size();
    }
    
    /**
     * Returns all player contexts.
     * 
     * @return Map of player contexts
     */
    public Map<UUID, PlayerContext> getAll() {
        return Map.copyOf(contexts);
    }
}
