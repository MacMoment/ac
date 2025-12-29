package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * Stub BukkitScheduler interface for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public interface BukkitScheduler {
    BukkitTask runTask(Plugin plugin, Runnable task);
    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay);
    BukkitTask runTaskAsync(Plugin plugin, Runnable task);
    BukkitTask runTaskLaterAsync(Plugin plugin, Runnable task, long delay);
}
