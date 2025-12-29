package org.bukkit.event.player;

import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Stub PlayerChangedWorldEvent for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public class PlayerChangedWorldEvent {
    private final Player player;
    private final World from;

    public PlayerChangedWorldEvent(Player player, World from) {
        this.player = player;
        this.from = from;
    }

    public Player getPlayer() { return player; }
    public World getFrom() { return from; }
}
