package com.macmoment.macac.ingest.impl;

import com.macmoment.macac.ingest.PacketIngestor;
import com.macmoment.macac.model.TelemetryInput;
import com.macmoment.macac.util.MonoClock;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Fallback ingestor using Bukkit's PlayerMoveEvent.
 * Used when ProtocolLib is not available.
 */
public final class FallbackEventIngestor implements PacketIngestor, Listener {
    
    private static final String NAME = "FallbackEvent";
    
    private final JavaPlugin plugin;
    private final MonoClock clock;
    private final Map<UUID, PlayerState> playerStates;
    
    private BiConsumer<Player, TelemetryInput> callback;
    private volatile boolean active;
    
    public FallbackEventIngestor(JavaPlugin plugin, MonoClock clock) {
        this.plugin = plugin;
        this.clock = clock;
        this.playerStates = new ConcurrentHashMap<>();
        this.active = false;
    }
    
    @Override
    public void start() {
        if (!active) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            active = true;
            plugin.getLogger().info("FallbackEventIngestor started");
        }
    }
    
    @Override
    public void stop() {
        if (active) {
            HandlerList.unregisterAll(this);
            playerStates.clear();
            active = false;
            plugin.getLogger().info("FallbackEventIngestor stopped");
        }
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public void setCallback(BiConsumer<Player, TelemetryInput> callback) {
        this.callback = callback;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (callback == null || !active) {
            return;
        }
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        long now = clock.nanoTime();
        
        // Get or create player state
        PlayerState state = playerStates.computeIfAbsent(playerId, id -> new PlayerState());
        
        // Calculate deltas
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        
        float yaw = to.getYaw();
        float pitch = to.getPitch();
        float deltaYaw = yaw - state.lastYaw;
        float deltaPitch = pitch - state.lastPitch;
        
        // Normalize yaw delta to [-180, 180]
        while (deltaYaw > 180) deltaYaw -= 360;
        while (deltaYaw < -180) deltaYaw += 360;
        
        long tickDelta = state.lastNanos > 0 ? now - state.lastNanos : 0;
        
        // Build telemetry input
        TelemetryInput input = TelemetryInput.builder()
            .dx(dx)
            .dy(dy)
            .dz(dz)
            .yaw(yaw)
            .pitch(pitch)
            .deltaYaw(deltaYaw)
            .deltaPitch(deltaPitch)
            .onGround(player.isOnGround())
            .inVehicle(player.isInsideVehicle())
            .teleporting(false) // Can't detect from move event alone
            .swimming(player.isSwimming())
            .gliding(player.isGliding())
            .climbing(player.isClimbing())
            .ping(player.getPing())
            .nanoTime(now)
            .tickDelta(tickDelta)
            .build();
        
        // Update state
        state.lastYaw = yaw;
        state.lastPitch = pitch;
        state.lastNanos = now;
        
        // Dispatch to callback
        callback.accept(player, input);
    }
    
    /**
     * Removes state for a player.
     * 
     * @param playerId Player UUID
     */
    public void removePlayer(UUID playerId) {
        playerStates.remove(playerId);
    }
    
    /**
     * Internal state tracking per player.
     */
    private static class PlayerState {
        float lastYaw = 0;
        float lastPitch = 0;
        long lastNanos = 0;
    }
}
