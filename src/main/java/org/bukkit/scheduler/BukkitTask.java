package org.bukkit.scheduler;

/**
 * Stub BukkitTask interface for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public interface BukkitTask {
    int getTaskId();
    void cancel();
    boolean isCancelled();
}
