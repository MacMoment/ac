package org.bukkit.entity;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Stub Player interface for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public interface Player {
    String getName();
    UUID getUniqueId();
    Location getLocation();
    GameMode getGameMode();
    boolean hasPermission(String permission);
    void sendMessage(String message);
    void kickPlayer(String message);
    boolean isOnline();
    boolean isOnGround();
    boolean isInsideVehicle();
    boolean isSwimming();
    boolean isGliding();
    boolean isClimbing();
    int getPing();
}
