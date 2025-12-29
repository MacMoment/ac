package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.Decision;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.Violation;
import com.macmoment.macac.util.MonoClock;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Applies mitigation policies to determine final decision on violations.
 * Handles exemptions, cooldowns, and fail-safe checks.
 */
public final class MitigationPolicy {
    
    private final MonoClock clock;
    
    // Configuration
    private long exemptionNanos;
    private long cooldownNanos;
    private long lagGraceNanos;
    private Set<UUID> whitelist;
    private String bypassPermission;
    private boolean exemptCreative;
    private boolean exemptSpectator;
    private double punishmentThreshold;
    private boolean punishmentEnabled;
    
    public MitigationPolicy(MonoClock clock) {
        this.clock = clock;
        this.exemptionNanos = 250_000_000L;
        this.cooldownNanos = 1_500_000_000L;
        this.lagGraceNanos = 500_000_000L;
        this.whitelist = Set.of();
        this.bypassPermission = "macac.bypass";
        this.exemptCreative = true;
        this.exemptSpectator = true;
        this.punishmentThreshold = 0.999;
        this.punishmentEnabled = false;
    }
    
    /**
     * Updates configuration.
     * 
     * @param config Engine configuration
     */
    public void configure(EngineConfig config) {
        this.exemptionNanos = config.getExemptionNanos();
        this.cooldownNanos = config.getCooldownNanos();
        this.lagGraceNanos = config.getLagGraceNanos();
        this.whitelist = config.getWhitelist();
        this.bypassPermission = config.getBypassPermission();
        this.exemptCreative = config.isExemptCreative();
        this.exemptSpectator = config.isExemptSpectator();
        this.punishmentThreshold = config.getPunishmentThreshold();
        this.punishmentEnabled = config.isPunishmentEnabled();
    }
    
    /**
     * Evaluates a violation and returns the appropriate decision.
     * 
     * @param violation The violation to evaluate
     * @param context Player context
     * @param player Bukkit player (may be null)
     * @return Decision indicating what action to take
     */
    public Decision evaluate(Violation violation, PlayerContext context, Player player) {
        if (violation == null) {
            return Decision.none("No violation");
        }
        
        long now = clock.nanoTime();
        
        // Check whitelist
        if (whitelist.contains(context.getPlayerId())) {
            return Decision.none("Player whitelisted");
        }
        
        // Check bypass permission
        if (player != null && bypassPermission != null && 
            player.hasPermission(bypassPermission)) {
            return Decision.none("Player has bypass permission");
        }
        
        // Check game mode exemptions
        if (player != null) {
            GameMode mode = player.getGameMode();
            if (exemptCreative && mode == GameMode.CREATIVE) {
                return Decision.none("Player in creative mode");
            }
            if (exemptSpectator && mode == GameMode.SPECTATOR) {
                return Decision.none("Player in spectator mode");
            }
        }
        
        // Check temporary exemption window
        if (context.isExempt(now)) {
            return Decision.none("Player in exemption window");
        }
        
        // Check cooldown
        if (context.isOnCooldown(now)) {
            return Decision.none("Alert on cooldown");
        }
        
        // Update cooldown for next alert
        context.setCooldownUntilNanos(now + cooldownNanos);
        context.setLastAlertNanos(now);
        context.incrementViolations();
        
        // Determine action level
        if (punishmentEnabled && violation.confidence() >= punishmentThreshold) {
            return Decision.punish(violation);
        }
        
        return Decision.alert(violation);
    }
    
    /**
     * Marks a player as temporarily exempt.
     * Call this after teleport, join, world change, etc.
     * 
     * @param context Player context
     */
    public void markExempt(PlayerContext context) {
        long now = clock.nanoTime();
        context.setExemptUntilNanos(now + exemptionNanos);
    }
    
    /**
     * Marks a player as exempt due to lag.
     * 
     * @param context Player context
     */
    public void markLagExempt(PlayerContext context) {
        long now = clock.nanoTime();
        context.setExemptUntilNanos(now + lagGraceNanos);
    }
    
    /**
     * Sets the teleporting flag for a player.
     * 
     * @param context Player context
     * @param teleporting Whether player is teleporting
     */
    public void setTeleporting(PlayerContext context, boolean teleporting) {
        context.setTeleporting(teleporting);
        if (!teleporting) {
            markExempt(context);
        }
    }
    
    /**
     * Sets the world changing flag for a player.
     * 
     * @param context Player context
     * @param changing Whether player is changing worlds
     */
    public void setWorldChanging(PlayerContext context, boolean changing) {
        context.setWorldChanging(changing);
        if (!changing) {
            markExempt(context);
        }
    }
    
    /**
     * Sets the recent join flag for a player.
     * 
     * @param context Player context
     * @param recentJoin Whether player recently joined
     */
    public void setRecentJoin(PlayerContext context, boolean recentJoin) {
        context.setRecentJoin(recentJoin);
        if (recentJoin) {
            markExempt(context);
        }
    }
}
