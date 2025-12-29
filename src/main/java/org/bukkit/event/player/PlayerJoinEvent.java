package org.bukkit.event.player;

import org.bukkit.entity.Player;

/**
 * Stub PlayerJoinEvent for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public class PlayerJoinEvent {
    private final Player player;

    public PlayerJoinEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }
}
