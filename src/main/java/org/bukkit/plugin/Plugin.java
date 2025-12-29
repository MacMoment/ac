package org.bukkit.plugin;

import org.bukkit.Server;

import java.util.logging.Logger;

/**
 * Stub Plugin interface for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public interface Plugin {
    String getName();
    Logger getLogger();
    Server getServer();
    void saveDefaultConfig();
    void reloadConfig();
}
