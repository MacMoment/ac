package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Stub PlayerMoveEvent for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public class PlayerMoveEvent {
    private final Player player;
    private final Location from;
    private final Location to;
    private boolean cancelled;

    public PlayerMoveEvent(Player player, Location from, Location to) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.cancelled = false;
    }

    public Player getPlayer() { return player; }
    public Location getFrom() { return from; }
    public Location getTo() { return to; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
