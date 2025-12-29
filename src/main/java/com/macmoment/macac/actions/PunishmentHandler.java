package com.macmoment.macac.actions;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.Decision;
import com.macmoment.macac.model.Violation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles punishment execution for confirmed violations.
 * Supports various punishment types with configurable thresholds.
 */
public final class PunishmentHandler {
    
    public enum PunishmentType {
        KICK,
        TEMP_MUTE,
        FLAG_ONLY
    }
    
    private final JavaPlugin plugin;
    private final Logger logger;
    
    private boolean enabled;
    private PunishmentType type;
    private double threshold;
    private long delayMs;
    
    public PunishmentHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = false;
        this.type = PunishmentType.FLAG_ONLY;
        this.threshold = 0.999;
        this.delayMs = 0;
    }
    
    /**
     * Updates configuration.
     * 
     * @param config Engine configuration
     */
    public void configure(EngineConfig config) {
        this.enabled = config.isPunishmentEnabled();
        this.threshold = config.getPunishmentThreshold();
        this.delayMs = config.getPunishmentDelayMs();
        
        String typeStr = config.getPunishmentType();
        try {
            this.type = PunishmentType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            this.type = PunishmentType.FLAG_ONLY;
            logger.warning("Invalid punishment type '" + typeStr + "', defaulting to FLAG_ONLY");
        }
    }
    
    /**
     * Executes punishment for a decision.
     * 
     * @param decision The decision to execute
     */
    public void execute(Decision decision) {
        if (!enabled || decision == null || decision.action() != Decision.Action.PUNISH) {
            return;
        }
        
        Violation violation = decision.violation();
        if (violation == null || violation.confidence() < threshold) {
            return;
        }
        
        UUID playerId = violation.playerId();
        Player player = Bukkit.getPlayer(playerId);
        
        if (player == null || !player.isOnline()) {
            logger.info("Cannot punish " + violation.playerName() + ": player offline");
            return;
        }
        
        // Schedule punishment (immediate if delay is 0)
        if (delayMs > 0) {
            long delayTicks = delayMs / 50; // Convert ms to ticks
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyPunishment(player, violation), delayTicks);
        } else {
            // Run on main thread
            Bukkit.getScheduler().runTask(plugin, () -> applyPunishment(player, violation));
        }
    }
    
    /**
     * Applies the actual punishment to a player.
     * 
     * @param player The player
     * @param violation The violation
     */
    private void applyPunishment(Player player, Violation violation) {
        if (!player.isOnline()) {
            return;
        }
        
        switch (type) {
            case KICK:
                String kickMessage = "You have been kicked for suspicious activity.\n" +
                    "Category: " + violation.category() + "\n" +
                    "If you believe this is an error, please contact server staff.";
                player.kickPlayer(kickMessage);
                logger.warning("Kicked " + player.getName() + " for " + violation.category() + 
                    " (confidence: " + String.format("%.4f", violation.confidence()) + ")");
                break;
                
            case TEMP_MUTE:
                // Note: Actual mute implementation would require a chat listener
                // This is a placeholder that logs the action
                logger.warning("TEMP_MUTE flagged for " + player.getName() + " for " + 
                    violation.category() + " - implement chat restriction externally");
                player.sendMessage("§cYou have been muted for suspicious chat patterns.");
                break;
                
            case FLAG_ONLY:
            default:
                // Just log, no action
                logger.info("FLAG: " + player.getName() + " flagged for " + violation.category() + 
                    " (confidence: " + String.format("%.4f", violation.confidence()) + ")");
                break;
        }
    }
    
    /**
     * Returns whether punishment is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
