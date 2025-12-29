package com.macmoment.macac.pipeline.checks;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CheckResult;
import com.macmoment.macac.model.Features;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.TelemetryInput;
import com.macmoment.macac.pipeline.Check;
import com.macmoment.macac.util.Stats;

import java.util.HashMap;
import java.util.Map;

/**
 * Analyzes movement patterns for physically impossible changes.
 * Uses movement deltas and basic physics expectations with ping tolerance.
 */
public final class MovementConsistencyCheck implements Check {
    
    private static final String NAME = "MovementConsistency";
    private static final String CATEGORY = "movement";
    
    // Minecraft physics constants
    private static final double GRAVITY = 0.08;  // blocks/tick^2
    private static final double DRAG = 0.98;     // velocity multiplier per tick
    private static final double SPRINT_FACTOR = 1.3;
    private static final double SCALE_FACTOR = 1.5;
    
    // Configuration
    private boolean enabled;
    private double weight;
    private double maxHorizSpeed;
    private double maxVertSpeed;
    private double accelTolerance;

    @Override
    public String getName() { return NAME; }

    @Override
    public String getCategory() { return CATEGORY; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public double getWeight() { return weight; }

    @Override
    public void configure(EngineConfig config) {
        this.enabled = config.isMovementConsistencyEnabled();
        this.weight = config.getMovementConsistencyWeight();
        this.maxHorizSpeed = config.getMaxHorizSpeed();
        this.maxVertSpeed = config.getMaxVertSpeed();
        this.accelTolerance = config.getAccelTolerance();
    }

    @Override
    public CheckResult analyze(TelemetryInput input, Features features, PlayerContext context) {
        if (!enabled) {
            return CheckResult.clean(NAME);
        }
        
        // Need history for acceleration analysis
        if (context.getFeatureHistory().size() < 2) {
            return CheckResult.clean(NAME);
        }
        
        // Skip if player has special movement states that override physics
        if (input.hasSpecialMovement()) {
            return CheckResult.clean(NAME);
        }
        
        double anomalyScore = 0.0;
        Map<String, Object> explain = new HashMap<>();
        
        // Calculate ping-adjusted tolerance
        // Higher ping = more tolerance for sudden changes
        double pingFactor = 1.0 + (context.getMedianPing() / 500.0);
        double adjustedMaxHoriz = maxHorizSpeed * pingFactor;
        double adjustedMaxVert = maxVertSpeed * pingFactor;
        
        // Check 1: Horizontal speed exceeds maximum
        double horizSpeed = features.horizSpeed();
        if (horizSpeed > adjustedMaxHoriz) {
            double excess = (horizSpeed - adjustedMaxHoriz) / adjustedMaxHoriz;
            anomalyScore += excess;
            explain.put("horizSpeedExcess", excess);
        }
        
        // Check 2: Vertical speed exceeds maximum (unless falling)
        double vertSpeed = Math.abs(features.vertSpeed());
        // Allow higher downward speed due to gravity
        double effectiveMaxVert = input.dy() < 0 ? adjustedMaxVert * 2.0 : adjustedMaxVert;
        if (vertSpeed > effectiveMaxVert) {
            double excess = (vertSpeed - effectiveMaxVert) / effectiveMaxVert;
            anomalyScore += excess;
            explain.put("vertSpeedExcess", excess);
        }
        
        // Check 3: Impossible acceleration (sudden speed changes)
        double horizAccel = Math.abs(features.horizAccel());
        double maxAccel = maxHorizSpeed * accelTolerance * pingFactor;
        if (horizAccel > maxAccel) {
            double excess = (horizAccel - maxAccel) / maxAccel;
            anomalyScore += excess * 0.5; // Weight lower since lag can cause this
            explain.put("horizAccelExcess", excess);
        }
        
        // Check 4: Ground state inconsistency
        // If player claims to be on ground but is moving up significantly
        if (input.onGround() && input.dy() > 0.1) {
            anomalyScore += 0.5;
            explain.put("groundStateAnomaly", true);
        }
        
        // Check 5: Direction change analysis
        // Look for physically impossible 180-degree turns at high speed
        Features prevFeatures = context.getFeatureHistory().get(0);
        if (prevFeatures != null) {
            double prevSpeed = prevFeatures.horizSpeed();
            double currSpeed = features.horizSpeed();
            
            // Both speeds significant and acceleration is extreme
            if (prevSpeed > 0.2 && currSpeed > 0.2 && horizAccel > prevSpeed * 2) {
                anomalyScore += 0.3;
                explain.put("suddenDirectionChange", true);
            }
        }
        
        // Convert to confidence
        double confidence = Stats.anomalyToConfidence(anomalyScore, SCALE_FACTOR);
        double severity = Math.min(1.0, anomalyScore / 2.0);
        
        if (confidence < 0.1) {
            return CheckResult.clean(NAME);
        }
        
        explain.put("horizSpeed", horizSpeed);
        explain.put("vertSpeed", features.vertSpeed());
        explain.put("horizAccel", horizAccel);
        explain.put("anomalyScore", anomalyScore);
        explain.put("pingFactor", pingFactor);
        
        return CheckResult.violation(NAME, confidence, severity, explain);
    }
}
