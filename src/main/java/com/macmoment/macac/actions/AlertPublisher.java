package com.macmoment.macac.actions;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.Violation;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Publishes alerts to staff and console.
 * Formats violations into readable messages.
 */
public final class AlertPublisher {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    
    private boolean enabled;
    private boolean consoleLog;
    private String format;
    private String alertPermission;
    
    public AlertPublisher(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = true;
        this.consoleLog = true;
        this.format = "&c[MacAC] &e{player} &7flagged for &f{category} " +
                      "&7(conf: &c{confidence}&7, sev: &e{severity}&7)";
        this.alertPermission = "macac.alerts";
    }
    
    /**
     * Updates configuration.
     * 
     * @param config Engine configuration
     */
    public void configure(EngineConfig config) {
        this.enabled = config.isAlertsEnabled();
        this.consoleLog = config.isConsoleLogEnabled();
        this.format = config.getAlertFormat();
    }
    
    /**
     * Publishes a violation alert.
     * 
     * @param violation The violation to publish
     */
    public void publish(Violation violation) {
        if (!enabled || violation == null) {
            return;
        }
        
        String message = formatMessage(violation);
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        
        // Log to console
        if (consoleLog) {
            // Strip color codes for console
            String plain = ChatColor.stripColor(colored);
            logger.warning(plain);
        }
        
        // Send to staff with permission
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(alertPermission)) {
                player.sendMessage(colored);
            }
        }
    }
    
    /**
     * Formats a violation into a message string.
     * 
     * @param violation The violation
     * @return Formatted message
     */
    private String formatMessage(Violation violation) {
        String result = format
            .replace("{player}", violation.playerName())
            .replace("{category}", violation.category())
            .replace("{confidence}", String.format("%.4f", violation.confidence()))
            .replace("{severity}", String.format("%.2f", violation.severity()))
            .replace("{explanation}", violation.getFormattedExplanation());
        
        return result;
    }
    
    /**
     * Publishes a debug message (only if debug mode enabled).
     * 
     * @param message Debug message
     * @param debug Whether debug mode is enabled
     */
    public void debug(String message, boolean debug) {
        if (debug) {
            logger.info("[DEBUG] " + message);
        }
    }
}
