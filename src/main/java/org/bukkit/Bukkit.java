package org.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Stub Bukkit class for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public final class Bukkit {
    private static Server server;
    private static Logger logger = Logger.getLogger("Bukkit");

    private Bukkit() {}

    public static void setServer(Server server) {
        Bukkit.server = server;
    }

    public static Server getServer() {
        return server;
    }

    public static Collection<? extends Player> getOnlinePlayers() {
        return server != null ? server.getOnlinePlayers() : java.util.Collections.emptyList();
    }

    public static Player getPlayer(String name) {
        return server != null ? server.getPlayer(name) : null;
    }

    public static Player getPlayer(UUID uuid) {
        return server != null ? server.getPlayer(uuid) : null;
    }

    public static PluginManager getPluginManager() {
        return server != null ? server.getPluginManager() : null;
    }

    public static org.bukkit.scheduler.BukkitScheduler getScheduler() {
        return server != null ? server.getScheduler() : null;
    }

    public static Logger getLogger() {
        return logger;
    }
}
