package com.macmoment.macac;

import com.macmoment.macac.core.Engine;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

/**
 * MacAC Anti-Cheat Plugin for Paper 1.20.1+
 * 
 * <p>A production-ready anti-cheat system featuring:
 * <ul>
 *   <li>Multi-layered detection (packet timing, movement physics, prediction)</li>
 *   <li>Confidence-based decisions with configurable thresholds</li>
 *   <li>Low false positive design with exemption windows and cooldowns</li>
 *   <li>Optional native acceleration via RDTSCP and AVX2 SIMD</li>
 *   <li>Optional ProtocolLib integration for packet-level analysis</li>
 * </ul>
 * 
 * <p>For configuration details, see {@code plugins/MacAC/config.yml}.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class MacACPlugin extends JavaPlugin implements Listener {
    
    /** Command name for the main admin command. */
    private static final String COMMAND_NAME = "macac";
    
    /** Permission required for admin commands. */
    private static final String ADMIN_PERMISSION = "macac.admin";
    
    /** Engine instance; null only before onEnable completes. */
    private Engine engine;
    
    /**
     * Called when the plugin is enabled.
     * 
     * <p>Initializes configuration, engine, and event listeners.
     */
    @Override
    public void onEnable() {
        getLogger().info("MacAC Anti-Cheat starting...");
        
        // Save default config if not present
        saveDefaultConfig();
        
        // Initialize and start engine
        engine = new Engine(this);
        engine.initialize();
        
        // Register this plugin as an event listener
        getServer().getPluginManager().registerEvents(this, this);
        
        // Start the engine
        engine.start();
        
        getLogger().info("MacAC Anti-Cheat enabled successfully!");
    }
    
    /**
     * Called when the plugin is disabled.
     * 
     * <p>Performs graceful shutdown of the engine.
     */
    @Override
    public void onDisable() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        getLogger().info("MacAC Anti-Cheat disabled.");
    }
    
    /**
     * Handles the main admin command.
     * 
     * @param sender command sender
     * @param command the command
     * @param label command label
     * @param args command arguments
     * @return true if command was handled
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, 
                            final String label, final String[] args) {
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(args, "args must not be null");
        
        if (!COMMAND_NAME.equalsIgnoreCase(command.getName())) {
            return false;
        }
        
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        final String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "exempt" -> handleExempt(sender, args);
            case "unexempt" -> handleUnexempt(sender, args);
            default -> sendHelp(sender);
        }
        
        return true;
    }
    
    /**
     * Handles the reload subcommand.
     */
    private void handleReload(final CommandSender sender) {
        if (engine != null) {
            engine.reload();
            sender.sendMessage(ChatColor.GREEN + "[MacAC] Configuration reloaded.");
        } else {
            sender.sendMessage(ChatColor.RED + "[MacAC] Engine not initialized.");
        }
    }
    
    /**
     * Handles the status subcommand.
     */
    private void handleStatus(final CommandSender sender) {
        if (engine == null) {
            sender.sendMessage(ChatColor.RED + "[MacAC] Engine not initialized.");
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== MacAC Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Running: " + 
            (engine.isRunning() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Players tracked: " + 
            ChatColor.WHITE + engine.getHistoryStore().size());
        sender.sendMessage(ChatColor.YELLOW + "Checks enabled: " + 
            ChatColor.WHITE + engine.getCheckRegistry().getEnabledChecks().size());
        sender.sendMessage(ChatColor.YELLOW + "Action threshold: " + 
            ChatColor.WHITE + String.format("%.4f", engine.getConfig().getActionConfidence()));
    }
    
    /**
     * Handles the exempt subcommand.
     */
    private void handleExempt(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /macac exempt <player>");
            return;
        }
        
        final String playerName = args[1];
        final Player target = getServer().getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
            return;
        }
        
        if (engine != null) {
            final UUID playerId = target.getUniqueId();
            engine.getWhitelistManager().addToWhitelist(playerId);
            sender.sendMessage(ChatColor.GREEN + "[MacAC] " + target.getName() + " is now exempt from checks.");
        }
    }
    
    /**
     * Handles the unexempt subcommand.
     */
    private void handleUnexempt(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /macac unexempt <player>");
            return;
        }
        
        final String playerName = args[1];
        final Player target = getServer().getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
            return;
        }
        
        if (engine != null) {
            final UUID playerId = target.getUniqueId();
            engine.getWhitelistManager().removeFromWhitelist(playerId);
            sender.sendMessage(ChatColor.GREEN + "[MacAC] " + target.getName() + " is no longer exempt.");
        }
    }
    
    /**
     * Sends help message with available commands.
     */
    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MacAC Anti-Cheat ===");
        sender.sendMessage(ChatColor.YELLOW + "/macac reload" + ChatColor.GRAY + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/macac status" + ChatColor.GRAY + " - Show engine status");
        sender.sendMessage(ChatColor.YELLOW + "/macac exempt <player>" + ChatColor.GRAY + " - Exempt a player");
        sender.sendMessage(ChatColor.YELLOW + "/macac unexempt <player>" + ChatColor.GRAY + " - Remove exemption");
    }
    
    // ========================================================================
    // Event Handlers
    // ========================================================================
    
    /**
     * Handles player join event.
     * 
     * <p>Initializes player context and sets exemption window for join grace period.
     * 
     * @param event player join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        if (engine == null) {
            return;
        }
        
        final Player player = event.getPlayer();
        engine.onPlayerJoin(player.getUniqueId(), player.getName());
    }
    
    /**
     * Handles player quit event.
     * 
     * <p>Cleans up player state and releases resources.
     * 
     * @param event player quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        if (engine == null) {
            return;
        }
        
        engine.onPlayerQuit(event.getPlayer().getUniqueId());
    }
    
    /**
     * Handles player teleport event.
     * 
     * <p>Sets exemption window to prevent false positives from large position changes.
     * 
     * @param event player teleport event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (engine == null) {
            return;
        }
        
        engine.onPlayerTeleport(event.getPlayer().getUniqueId());
    }
    
    /**
     * Handles world change event.
     * 
     * <p>Resets player context and sets exemption window for world transition.
     * 
     * @param event world change event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        if (engine == null) {
            return;
        }
        
        engine.onWorldChange(event.getPlayer().getUniqueId());
    }
    
    // ========================================================================
    // Public API
    // ========================================================================
    
    /**
     * Returns the anti-cheat engine instance.
     * 
     * <p>This method is intended for integration with other plugins.
     * The engine may be null before {@code onEnable} completes or after
     * {@code onDisable} is called.
     * 
     * @return engine instance, or null if plugin is not fully enabled
     */
    public Engine getEngine() {
        return engine;
    }
}
