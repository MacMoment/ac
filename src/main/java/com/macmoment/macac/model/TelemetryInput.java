package com.macmoment.macac.model;

/**
 * Normalized telemetry input from a player movement or packet.
 * Immutable record containing all raw data needed for analysis.
 */
public record TelemetryInput(
    // Movement deltas
    double dx,          // X-axis movement delta
    double dy,          // Y-axis movement delta (vertical)
    double dz,          // Z-axis movement delta
    
    // Rotation
    float yaw,          // Current yaw
    float pitch,        // Current pitch
    float deltaYaw,     // Yaw change since last input
    float deltaPitch,   // Pitch change since last input
    
    // State flags
    boolean onGround,   // Player reports being on ground
    boolean inVehicle,  // Player is in a vehicle
    boolean teleporting,// Player is teleporting (exemption)
    boolean swimming,   // Player is swimming
    boolean gliding,    // Player is gliding with elytra
    boolean climbing,   // Player is on a ladder/vine
    
    // Network state
    long ping,          // Current ping in milliseconds
    
    // Timing
    long nanoTime,      // Monotonic timestamp of this input
    long tickDelta      // Time since last input in nanoseconds
) {
    /**
     * Creates a builder for constructing TelemetryInput instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the horizontal speed (XZ plane magnitude).
     */
    public double horizontalSpeed() {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Returns the 3D speed magnitude.
     */
    public double speed3D() {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Returns true if the player has any special movement state.
     */
    public boolean hasSpecialMovement() {
        return inVehicle || teleporting || swimming || gliding || climbing;
    }

    /**
     * Builder for TelemetryInput.
     */
    public static class Builder {
        private double dx, dy, dz;
        private float yaw, pitch, deltaYaw, deltaPitch;
        private boolean onGround, inVehicle, teleporting, swimming, gliding, climbing;
        private long ping;
        private long nanoTime;
        private long tickDelta;

        public Builder dx(double dx) { this.dx = dx; return this; }
        public Builder dy(double dy) { this.dy = dy; return this; }
        public Builder dz(double dz) { this.dz = dz; return this; }
        public Builder yaw(float yaw) { this.yaw = yaw; return this; }
        public Builder pitch(float pitch) { this.pitch = pitch; return this; }
        public Builder deltaYaw(float deltaYaw) { this.deltaYaw = deltaYaw; return this; }
        public Builder deltaPitch(float deltaPitch) { this.deltaPitch = deltaPitch; return this; }
        public Builder onGround(boolean onGround) { this.onGround = onGround; return this; }
        public Builder inVehicle(boolean inVehicle) { this.inVehicle = inVehicle; return this; }
        public Builder teleporting(boolean teleporting) { this.teleporting = teleporting; return this; }
        public Builder swimming(boolean swimming) { this.swimming = swimming; return this; }
        public Builder gliding(boolean gliding) { this.gliding = gliding; return this; }
        public Builder climbing(boolean climbing) { this.climbing = climbing; return this; }
        public Builder ping(long ping) { this.ping = ping; return this; }
        public Builder nanoTime(long nanoTime) { this.nanoTime = nanoTime; return this; }
        public Builder tickDelta(long tickDelta) { this.tickDelta = tickDelta; return this; }

        public TelemetryInput build() {
            return new TelemetryInput(
                dx, dy, dz,
                yaw, pitch, deltaYaw, deltaPitch,
                onGround, inVehicle, teleporting, swimming, gliding, climbing,
                ping, nanoTime, tickDelta
            );
        }
    }
}
