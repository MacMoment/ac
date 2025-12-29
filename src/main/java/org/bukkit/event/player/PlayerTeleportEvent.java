package org.bukkit.event.player;

import org.bukkit.entity.Player;

/**
 * Stub PlayerTeleportEvent for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public class PlayerTeleportEvent {
    private final Player player;
    private boolean cancelled;

    public PlayerTeleportEvent(Player player) {
        this.player = player;
        this.cancelled = false;
    }

    public Player getPlayer() { return player; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
