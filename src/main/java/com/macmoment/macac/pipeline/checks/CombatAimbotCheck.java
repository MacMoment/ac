package com.macmoment.macac.pipeline.checks;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CombatContext;
import com.macmoment.macac.model.CombatInput;
import com.macmoment.macac.util.Stats;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects aimbot and aim assist by analyzing rotation patterns during combat.
 * 
 * Detection signals:
 * - Unnaturally perfect aim (too consistent aim error)
 * - Snap aiming (instant rotation to target)
 * - Robotic rotation patterns (lack of natural jitter)
 * - Impossible rotation speeds
 * 
 * Uses RDTSCP timing for high-precision attack interval analysis.
 */
public final class CombatAimbotCheck {
    
    private static final String NAME = "CombatAimbot";
    private static final String CATEGORY = "combat";
    private static final double SCALE_FACTOR = 1.5;
    
    // Configuration
    private boolean enabled;
    private double weight;
    private double maxSnapAngle;           // Max instant rotation in degrees
    private double minAimVariance;         // Minimum expected aim variance (too perfect = suspicious)
    private double maxAimPerfection;       // Max aim accuracy before suspicious
    private int minSamplesRequired;        // Minimum samples before analysis
    
    // Human aim characteristics (based on research)
    private static final double HUMAN_MIN_AIM_JITTER = 0.5;   // Degrees
    private static final double HUMAN_MAX_SNAP_SPEED = 180.0; // Degrees per tick
    private static final double PERFECT_AIM_THRESHOLD = 2.0;  // Degrees error

    public String getName() { return NAME; }
    public String getCategory() { return CATEGORY; }
    public boolean isEnabled() { return enabled; }
    public double getWeight() { return weight; }
    
    public void configure(EngineConfig config) {
        this.enabled = config.isCombatAimbotEnabled();
        this.weight = config.getCombatAimbotWeight();
        this.maxSnapAngle = config.getCombatMaxSnapAngle();
        this.minAimVariance = config.getCombatMinAimVariance();
        this.maxAimPerfection = config.getCombatMaxAimPerfection();
        this.minSamplesRequired = config.getCombatMinSamples();
    }
    
    /**
     * Analyzes combat input for aimbot patterns.
     * 
     * @param input Current combat input
     * @param context Combat context with history
     * @return Analysis result with confidence and explanation
     */
    public CombatCheckResult analyze(CombatInput input, CombatContext context) {
        if (!enabled) {
            return CombatCheckResult.clean(NAME);
        }
        
        // Need enough samples for meaningful analysis
        if (context.getAimErrorWindow().size() < minSamplesRequired) {
            return CombatCheckResult.clean(NAME);
        }
        
        double anomalyScore = 0.0;
        Map<String, Object> explain = new HashMap<>();
        
        // === Analysis 1: Snap Aim Detection ===
        // Detect instant rotation to targets (aimbot snap)
        double snapAngle = input.snapAngle();
        if (snapAngle > maxSnapAngle) {
            // Large instant rotation - check if it perfectly aligned with target
            double aimError = input.aimError();
            if (aimError < PERFECT_AIM_THRESHOLD) {
                // Perfect snap to target - highly suspicious
                double snapAnomaly = (snapAngle / maxSnapAngle) * (1.0 - aimError / PERFECT_AIM_THRESHOLD);
                anomalyScore += snapAnomaly;
                explain.put("snapAngle", snapAngle);
                explain.put("snapAnomaly", snapAnomaly);
            }
        }
        
        // === Analysis 2: Aim Consistency Detection ===
        // Aimbots have unnaturally consistent aim (too perfect)
        double aimVariance = context.getAimErrorWindow().stdDev();
        double meanAimError = context.getAimErrorWindow().mean();
        
        if (aimVariance < minAimVariance && meanAimError < maxAimPerfection) {
            // Too consistent and too accurate
            double consistencyAnomaly = (1.0 - aimVariance / minAimVariance) * 
                                        (1.0 - meanAimError / maxAimPerfection);
            anomalyScore += consistencyAnomaly;
            explain.put("aimVariance", aimVariance);
            explain.put("meanAimError", meanAimError);
            explain.put("consistencyAnomaly", consistencyAnomaly);
        }
        
        // === Analysis 3: Statistical Aim Pattern ===
        // Human aim follows certain statistical distributions
        // Aimbots often have abnormal distributions
        double aimMedian = context.getAimErrorWindow().median();
        double aimMad = context.getAimErrorWindow().mad();
        
        // Very low MAD indicates robotic precision
        if (aimMad < HUMAN_MIN_AIM_JITTER && context.getAimErrorWindow().size() >= minSamplesRequired * 2) {
            double roboticAnomaly = 1.0 - (aimMad / HUMAN_MIN_AIM_JITTER);
            anomalyScore += roboticAnomaly * 0.5;
            explain.put("aimMad", aimMad);
            explain.put("roboticAnomaly", roboticAnomaly);
        }
        
        // === Analysis 4: Multi-Target Snap Analysis ===
        // Detect instant target switching with perfect aim
        if (input.targetId() != null && context.getLastTargetId() != null &&
            !input.targetId().equals(context.getLastTargetId())) {
            // Target switched - analyze the transition
            if (snapAngle > 30.0 && input.aimError() < PERFECT_AIM_THRESHOLD) {
                double switchAnomaly = (snapAngle / 90.0) * 0.5;
                anomalyScore += switchAnomaly;
                explain.put("targetSwitchSnap", snapAngle);
            }
        }
        
        // === Analysis 5: Rotation Speed Analysis ===
        // Check for humanly impossible rotation speeds
        if (input.timeSinceLastAttack() > 0) {
            double timeSeconds = input.timeSinceLastAttack() / 1_000_000_000.0;
            double rotationSpeed = snapAngle / timeSeconds; // Degrees per second
            
            // Adjust for tick rate (50ms per tick = 20 ticks/sec)
            double rotationPerTick = snapAngle / (timeSeconds * 20.0);
            
            if (rotationPerTick > HUMAN_MAX_SNAP_SPEED) {
                double speedAnomaly = (rotationPerTick - HUMAN_MAX_SNAP_SPEED) / HUMAN_MAX_SNAP_SPEED;
                anomalyScore += speedAnomaly * 0.3;
                explain.put("rotationSpeed", rotationSpeed);
                explain.put("rotationPerTick", rotationPerTick);
            }
        }
        
        // Convert to confidence using sigmoid transformation
        double confidence = Stats.anomalyToConfidence(anomalyScore, SCALE_FACTOR);
        double severity = Math.min(1.0, anomalyScore / 2.5);
        
        if (confidence < 0.1) {
            return CombatCheckResult.clean(NAME);
        }
        
        explain.put("anomalyScore", anomalyScore);
        explain.put("currentAimError", input.aimError());
        explain.put("hitRate", context.getRecentHitRate());
        
        return CombatCheckResult.violation(NAME, confidence, severity, explain);
    }
    
    /**
     * Result of aimbot analysis.
     */
    public record CombatCheckResult(
        String checkName,
        double confidence,
        double severity,
        Map<String, Object> explanation,
        boolean isViolation
    ) {
        public static CombatCheckResult clean(String name) {
            return new CombatCheckResult(name, 0.0, 0.0, Map.of(), false);
        }
        
        public static CombatCheckResult violation(String name, double confidence, 
                                                  double severity, Map<String, Object> explain) {
            return new CombatCheckResult(name, confidence, severity, explain, true);
        }
    }
}
