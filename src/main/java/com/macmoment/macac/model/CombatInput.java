package com.macmoment.macac.model;

import java.util.UUID;

/**
 * Combat telemetry input from a player attack event.
 * Immutable record containing all data needed for combat analysis.
 */
public record CombatInput(
    // Attacker info
    UUID attackerId,        // Attacker player UUID
    String attackerName,    // Attacker name
    
    // Target info  
    UUID targetId,          // Target entity UUID (null if miss)
    double targetX,         // Target X position
    double targetY,         // Target Y position
    double targetZ,         // Target Z position
    
    // Attack vector
    double attackerX,       // Attacker X position at attack time
    double attackerY,       // Attacker Y position
    double attackerZ,       // Attacker Z position
    float attackerYaw,      // Attacker yaw at attack
    float attackerPitch,    // Attacker pitch at attack
    
    // Pre-attack rotation (for snap detection)
    float preAttackYaw,     // Yaw before the attack frame
    float preAttackPitch,   // Pitch before the attack frame
    
    // Timing
    long nanoTime,          // Monotonic timestamp of attack
    long timeSinceLastAttack, // Nanoseconds since last attack
    
    // Attack result
    boolean hit,            // Whether attack connected
    double damage,          // Damage dealt
    boolean critical,       // Was it a critical hit
    
    // Network state
    long ping               // Current ping in milliseconds
) {
    
    /**
     * Creates a builder for constructing CombatInput instances.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Calculates the distance to target.
     * 
     * @return 3D distance to target
     */
    public double distanceToTarget() {
        double dx = targetX - attackerX;
        double dy = targetY - attackerY;
        double dz = targetZ - attackerZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Calculates the horizontal distance to target.
     * 
     * @return XZ plane distance to target
     */
    public double horizontalDistanceToTarget() {
        double dx = targetX - attackerX;
        double dz = targetZ - attackerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Calculates the angle change from pre-attack to attack (snap detection).
     * 
     * @return Total rotation change in degrees
     */
    public double snapAngle() {
        float yawDiff = Math.abs(attackerYaw - preAttackYaw);
        float pitchDiff = Math.abs(attackerPitch - preAttackPitch);
        
        // Normalize yaw difference
        if (yawDiff > 180) yawDiff = 360 - yawDiff;
        
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }
    
    /**
     * Calculates the expected yaw to face the target.
     * 
     * @return Expected yaw in degrees
     */
    public float expectedYaw() {
        double dx = targetX - attackerX;
        double dz = targetZ - attackerZ;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
    
    /**
     * Calculates the expected pitch to face the target.
     * 
     * @return Expected pitch in degrees
     */
    public float expectedPitch() {
        double dx = targetX - attackerX;
        double dy = targetY - (attackerY + 1.62); // Eye height offset
        double dz = targetZ - attackerZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
    }
    
    /**
     * Calculates how accurately the attacker was aiming at the target.
     * 
     * @return Angle error in degrees (0 = perfect aim)
     */
    public double aimError() {
        float expectedYaw = expectedYaw();
        float expectedPitch = expectedPitch();
        
        float yawError = Math.abs(attackerYaw - expectedYaw);
        if (yawError > 180) yawError = 360 - yawError;
        
        float pitchError = Math.abs(attackerPitch - expectedPitch);
        
        return Math.sqrt(yawError * yawError + pitchError * pitchError);
    }

    /**
     * Builder for CombatInput.
     */
    public static class Builder {
        private UUID attackerId;
        private String attackerName;
        private UUID targetId;
        private double targetX, targetY, targetZ;
        private double attackerX, attackerY, attackerZ;
        private float attackerYaw, attackerPitch;
        private float preAttackYaw, preAttackPitch;
        private long nanoTime;
        private long timeSinceLastAttack;
        private boolean hit;
        private double damage;
        private boolean critical;
        private long ping;

        public Builder attackerId(UUID attackerId) { this.attackerId = attackerId; return this; }
        public Builder attackerName(String attackerName) { this.attackerName = attackerName; return this; }
        public Builder targetId(UUID targetId) { this.targetId = targetId; return this; }
        public Builder targetX(double targetX) { this.targetX = targetX; return this; }
        public Builder targetY(double targetY) { this.targetY = targetY; return this; }
        public Builder targetZ(double targetZ) { this.targetZ = targetZ; return this; }
        public Builder attackerX(double attackerX) { this.attackerX = attackerX; return this; }
        public Builder attackerY(double attackerY) { this.attackerY = attackerY; return this; }
        public Builder attackerZ(double attackerZ) { this.attackerZ = attackerZ; return this; }
        public Builder attackerYaw(float attackerYaw) { this.attackerYaw = attackerYaw; return this; }
        public Builder attackerPitch(float attackerPitch) { this.attackerPitch = attackerPitch; return this; }
        public Builder preAttackYaw(float preAttackYaw) { this.preAttackYaw = preAttackYaw; return this; }
        public Builder preAttackPitch(float preAttackPitch) { this.preAttackPitch = preAttackPitch; return this; }
        public Builder nanoTime(long nanoTime) { this.nanoTime = nanoTime; return this; }
        public Builder timeSinceLastAttack(long timeSinceLastAttack) { this.timeSinceLastAttack = timeSinceLastAttack; return this; }
        public Builder hit(boolean hit) { this.hit = hit; return this; }
        public Builder damage(double damage) { this.damage = damage; return this; }
        public Builder critical(boolean critical) { this.critical = critical; return this; }
        public Builder ping(long ping) { this.ping = ping; return this; }

        public CombatInput build() {
            return new CombatInput(
                attackerId, attackerName, targetId,
                targetX, targetY, targetZ,
                attackerX, attackerY, attackerZ,
                attackerYaw, attackerPitch,
                preAttackYaw, preAttackPitch,
                nanoTime, timeSinceLastAttack,
                hit, damage, critical, ping
            );
        }
    }
}
