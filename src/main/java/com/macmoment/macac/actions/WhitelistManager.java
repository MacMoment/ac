package com.macmoment.macac.actions;

import com.macmoment.macac.config.EngineConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages player exemptions and whitelisting.
 * Provides manual and automatic exemption management.
 */
public final class WhitelistManager {
    
    private final Set<UUID> whitelist;
    private final Set<UUID> temporaryExemptions;
    
    public WhitelistManager() {
        this.whitelist = new HashSet<>();
        this.temporaryExemptions = new HashSet<>();
    }
    
    /**
     * Updates configuration.
     * 
     * @param config Engine configuration
     */
    public void configure(EngineConfig config) {
        whitelist.clear();
        whitelist.addAll(config.getWhitelist());
    }
    
    /**
     * Adds a player to the permanent whitelist.
     * 
     * @param playerId Player UUID
     */
    public void addToWhitelist(UUID playerId) {
        whitelist.add(playerId);
    }
    
    /**
     * Removes a player from the permanent whitelist.
     * 
     * @param playerId Player UUID
     */
    public void removeFromWhitelist(UUID playerId) {
        whitelist.remove(playerId);
    }
    
    /**
     * Checks if a player is on the whitelist.
     * 
     * @param playerId Player UUID
     * @return true if whitelisted
     */
    public boolean isWhitelisted(UUID playerId) {
        return whitelist.contains(playerId);
    }
    
    /**
     * Adds a temporary exemption for a player.
     * 
     * @param playerId Player UUID
     */
    public void addTemporaryExemption(UUID playerId) {
        temporaryExemptions.add(playerId);
    }
    
    /**
     * Removes a temporary exemption for a player.
     * 
     * @param playerId Player UUID
     */
    public void removeTemporaryExemption(UUID playerId) {
        temporaryExemptions.remove(playerId);
    }
    
    /**
     * Checks if a player has a temporary exemption.
     * 
     * @param playerId Player UUID
     * @return true if exempt
     */
    public boolean hasTemporaryExemption(UUID playerId) {
        return temporaryExemptions.contains(playerId);
    }
    
    /**
     * Checks if a player is exempt (whitelist or temporary).
     * 
     * @param playerId Player UUID
     * @return true if exempt
     */
    public boolean isExempt(UUID playerId) {
        return whitelist.contains(playerId) || temporaryExemptions.contains(playerId);
    }
    
    /**
     * Returns the whitelist.
     * 
     * @return Set of whitelisted UUIDs
     */
    public Set<UUID> getWhitelist() {
        return Set.copyOf(whitelist);
    }
    
    /**
     * Clears all temporary exemptions.
     */
    public void clearTemporaryExemptions() {
        temporaryExemptions.clear();
    }
}
