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

import java.util.UUID;

/**
 * MacAC Anti-Cheat Plugin for Paper 1.20.1+
 * 
 * A production-ready anti-cheat with multi-layered detection,
 * confidence-based decisions, and low false positive design.
 */
public final class MacACPlugin extends JavaPlugin implements Listener {
    
    private Engine engine;
    
    @Override
    public void onEnable() {
        getLogger().info("MacAC Anti-Cheat starting...");
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize engine
        engine = new Engine(this);
        engine.initialize();
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);
        
        // Start engine
        engine.start();
        
        getLogger().info("MacAC Anti-Cheat enabled successfully!");
    }
    
    @Override
    public void onDisable() {
        if (engine != null) {
            engine.stop();
        }
        getLogger().info("MacAC Anti-Cheat disabled.");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("macac")) {
            return false;
        }
        
        if (!sender.hasPermission("macac.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                engine.reload();
                sender.sendMessage(ChatColor.GREEN + "[MacAC] Configuration reloaded.");
                break;
                
            case "status":
                sendStatus(sender);
                break;
                
            case "exempt":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /macac exempt <player>");
                    return true;
                }
                handleExempt(sender, args[1]);
                break;
                
            case "unexempt":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /macac unexempt <player>");
                    return true;
                }
                handleUnexempt(sender, args[1]);
                break;
                
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MacAC Anti-Cheat ===");
        sender.sendMessage(ChatColor.YELLOW + "/macac reload" + ChatColor.GRAY + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/macac status" + ChatColor.GRAY + " - Show engine status");
        sender.sendMessage(ChatColor.YELLOW + "/macac exempt <player>" + ChatColor.GRAY + " - Exempt a player");
        sender.sendMessage(ChatColor.YELLOW + "/macac unexempt <player>" + ChatColor.GRAY + " - Remove exemption");
    }
    
    private void sendStatus(CommandSender sender) {
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
    
    private void handleExempt(CommandSender sender, String playerName) {
        Player target = getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
            return;
        }
        
        UUID playerId = target.getUniqueId();
        engine.getWhitelistManager().addToWhitelist(playerId);
        sender.sendMessage(ChatColor.GREEN + "[MacAC] " + target.getName() + " is now exempt from checks.");
    }
    
    private void handleUnexempt(CommandSender sender, String playerName) {
        Player target = getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
            return;
        }
        
        UUID playerId = target.getUniqueId();
        engine.getWhitelistManager().removeFromWhitelist(playerId);
        sender.sendMessage(ChatColor.GREEN + "[MacAC] " + target.getName() + " is no longer exempt.");
    }
    
    // Event handlers
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        engine.onPlayerJoin(player.getUniqueId(), player.getName());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        engine.onPlayerQuit(event.getPlayer().getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        engine.onPlayerTeleport(event.getPlayer().getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        engine.onWorldChange(event.getPlayer().getUniqueId());
    }
    
    /**
     * Returns the engine instance.
     * 
     * @return Engine instance
     */
    public Engine getEngine() {
        return engine;
    }
}
