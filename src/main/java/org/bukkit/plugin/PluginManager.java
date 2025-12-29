package org.bukkit.plugin;

import org.bukkit.event.Listener;

/**
 * Stub PluginManager interface for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);
    Plugin getPlugin(String name);
}
