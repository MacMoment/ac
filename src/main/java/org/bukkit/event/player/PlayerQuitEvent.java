package org.bukkit.event.player;

import org.bukkit.entity.Player;

/**
 * Stub PlayerQuitEvent for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public class PlayerQuitEvent {
    private final Player player;

    public PlayerQuitEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }
}
