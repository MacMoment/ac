package com.macmoment.macac.actions;

import com.macmoment.macac.config.EngineConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player exemptions and whitelisting for the anti-cheat system.
 * 
 * <p>This manager handles two types of exemptions:
 * <ul>
 *   <li><strong>Permanent whitelist</strong>: Players who should never be checked,
 *       typically configured administrators or known-good accounts</li>
 *   <li><strong>Temporary exemptions</strong>: Short-term exemptions for players
 *       experiencing network issues or during specific game events</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> All methods are thread-safe. The underlying
 * collections use concurrent data structures for safe access from multiple threads.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class WhitelistManager {
    
    /** Permanently whitelisted players. */
    private final Set<UUID> whitelist;
    
    /** Temporarily exempted players. */
    private final Set<UUID> temporaryExemptions;
    
    /**
     * Creates a new whitelist manager with empty whitelist and exemptions.
     */
    public WhitelistManager() {
        // Use concurrent sets for thread-safe access
        this.whitelist = ConcurrentHashMap.newKeySet();
        this.temporaryExemptions = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Configures the manager from engine configuration.
     * 
     * <p>This replaces the current whitelist with the one from configuration.
     * Temporary exemptions are not affected.
     * 
     * @param config engine configuration; must not be null
     */
    public void configure(final EngineConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        
        whitelist.clear();
        
        final Set<UUID> configuredWhitelist = config.getWhitelist();
        if (configuredWhitelist != null) {
            whitelist.addAll(configuredWhitelist);
        }
    }
    
    /**
     * Adds a player to the permanent whitelist.
     * 
     * <p>Whitelisted players are never checked by any anti-cheat system.
     * 
     * @param playerId player UUID; must not be null
     * @throws NullPointerException if playerId is null
     */
    public void addToWhitelist(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        whitelist.add(playerId);
    }
    
    /**
     * Removes a player from the permanent whitelist.
     * 
     * @param playerId player UUID; must not be null
     * @throws NullPointerException if playerId is null
     */
    public void removeFromWhitelist(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        whitelist.remove(playerId);
    }
    
    /**
     * Checks if a player is on the permanent whitelist.
     * 
     * @param playerId player UUID; must not be null
     * @return true if player is whitelisted
     * @throws NullPointerException if playerId is null
     */
    public boolean isWhitelisted(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return whitelist.contains(playerId);
    }
    
    /**
     * Adds a temporary exemption for a player.
     * 
     * <p>Temporary exemptions are typically used for short-term situations
     * like lag spikes or specific game events. Unlike the permanent whitelist,
     * these are expected to be removed after the situation resolves.
     * 
     * @param playerId player UUID; must not be null
     * @throws NullPointerException if playerId is null
     */
    public void addTemporaryExemption(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        temporaryExemptions.add(playerId);
    }
    
    /**
     * Removes a temporary exemption for a player.
     * 
     * @param playerId player UUID; must not be null
     * @throws NullPointerException if playerId is null
     */
    public void removeTemporaryExemption(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        temporaryExemptions.remove(playerId);
    }
    
    /**
     * Checks if a player has a temporary exemption.
     * 
     * @param playerId player UUID; must not be null
     * @return true if player has a temporary exemption
     * @throws NullPointerException if playerId is null
     */
    public boolean hasTemporaryExemption(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return temporaryExemptions.contains(playerId);
    }
    
    /**
     * Checks if a player is exempt from checks (either whitelisted or temporarily exempt).
     * 
     * <p>This is the primary method to call when determining whether to skip
     * checks for a player. It combines both whitelist and temporary exemption status.
     * 
     * @param playerId player UUID; must not be null
     * @return true if player is exempt from all checks
     * @throws NullPointerException if playerId is null
     */
    public boolean isExempt(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return whitelist.contains(playerId) || temporaryExemptions.contains(playerId);
    }
    
    /**
     * Returns a defensive copy of the current whitelist.
     * 
     * <p>The returned set is immutable and reflects a snapshot of the whitelist
     * at the time of this call.
     * 
     * @return immutable copy of whitelisted UUIDs; never null
     */
    public Set<UUID> getWhitelist() {
        return Collections.unmodifiableSet(new HashSet<>(whitelist));
    }
    
    /**
     * Returns the number of permanently whitelisted players.
     * 
     * @return whitelist size
     */
    public int getWhitelistSize() {
        return whitelist.size();
    }
    
    /**
     * Returns the number of temporarily exempted players.
     * 
     * @return temporary exemption count
     */
    public int getTemporaryExemptionCount() {
        return temporaryExemptions.size();
    }
    
    /**
     * Removes all temporary exemptions.
     * 
     * <p>This does not affect the permanent whitelist.
     */
    public void clearTemporaryExemptions() {
        temporaryExemptions.clear();
    }
    
    /**
     * Removes all exemptions for a player (both whitelist and temporary).
     * 
     * @param playerId player UUID; must not be null
     * @throws NullPointerException if playerId is null
     */
    public void clearAllExemptions(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        whitelist.remove(playerId);
        temporaryExemptions.remove(playerId);
    }
}
