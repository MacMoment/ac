package org.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Collection;
import java.util.UUID;

/**
 * Stub Server interface for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public interface Server {
    String getName();
    Collection<? extends Player> getOnlinePlayers();
    Player getPlayer(String name);
    Player getPlayer(UUID uuid);
    PluginManager getPluginManager();
    BukkitScheduler getScheduler();
}
