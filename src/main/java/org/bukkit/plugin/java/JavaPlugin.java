package org.bukkit.plugin.java;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Stub JavaPlugin class for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public abstract class JavaPlugin implements Plugin {
    private Logger logger;
    private Server server;
    private FileConfiguration config;
    private String name = "MacAC";

    public JavaPlugin() {
        this.logger = Logger.getLogger(getName());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    @Override
    public void saveDefaultConfig() {
        // Stub implementation
    }

    @Override
    public void reloadConfig() {
        // Stub implementation
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            config = new FileConfiguration();
        }
        return config;
    }

    public void onEnable() {
        // Override in subclass
    }

    public void onDisable() {
        // Override in subclass
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }
}
