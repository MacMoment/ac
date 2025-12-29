package org.bukkit.command;

/**
 * Stub CommandSender interface for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public interface CommandSender {
    void sendMessage(String message);
    boolean hasPermission(String permission);
    String getName();
}
