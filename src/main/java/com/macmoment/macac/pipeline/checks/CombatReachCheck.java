package com.macmoment.macac.pipeline.checks;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CombatCheckResult;
import com.macmoment.macac.model.CombatContext;
import com.macmoment.macac.model.CombatInput;
import com.macmoment.macac.util.Stats;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects reach hacks by analyzing attack distances.
 * 
 * Detection signals:
 * - Attacks from beyond vanilla reach (3.0 blocks)
 * - Consistently hitting at maximum reach
 * - Statistical anomalies in reach distribution
 * - Ping-adjusted reach validation
 * 
 * Uses native SIMD-optimized statistics for fast reach analysis.
 */
public final class CombatReachCheck {
    
    private static final String NAME = "CombatReach";
    private static final String CATEGORY = "combat";
    private static final double SCALE_FACTOR = 2.0;
    
    // Configuration
    private boolean enabled;
    private double weight;
    private double maxReach;               // Maximum allowed reach in blocks
    private double reachBuffer;            // Buffer for ping compensation
    private int minSamplesRequired;        // Minimum samples before analysis
    
    // Vanilla Minecraft constants
    private static final double VANILLA_REACH = 3.0;          // Blocks
    private static final double VANILLA_CREATIVE_REACH = 5.0; // Blocks (creative mode)
    private static final double PING_COMPENSATION_FACTOR = 0.001; // Extra reach per ms ping

    public String getName() { return NAME; }
    public String getCategory() { return CATEGORY; }
    public boolean isEnabled() { return enabled; }
    public double getWeight() { return weight; }
    
    public void configure(EngineConfig config) {
        this.enabled = config.isCombatReachEnabled();
        this.weight = config.getCombatReachWeight();
        this.maxReach = config.getCombatMaxReach();
        this.reachBuffer = config.getCombatReachBuffer();
        this.minSamplesRequired = config.getCombatMinSamples();
    }
    
    /**
     * Analyzes combat input for reach violations.
     * 
     * @param input Current combat input
     * @param context Combat context with history
     * @return Analysis result with confidence and explanation
     */
    public CombatCheckResult analyze(CombatInput input, CombatContext context) {
        if (!enabled) {
            return CombatCheckResult.clean(NAME);
        }
        
        // Only analyze hits (misses don't tell us about reach)
        if (!input.hit()) {
            return CombatCheckResult.clean(NAME);
        }
        
        double anomalyScore = 0.0;
        Map<String, Object> explain = new HashMap<>();
        
        // Calculate actual reach
        double actualReach = input.distanceToTarget();
        double horizontalReach = input.horizontalDistanceToTarget();
        
        // Calculate ping-adjusted max reach
        double pingCompensation = input.ping() * PING_COMPENSATION_FACTOR;
        double adjustedMaxReach = maxReach + reachBuffer + pingCompensation;
        
        // === Analysis 1: Direct Reach Violation ===
        // Check if reach exceeds maximum allowed
        if (actualReach > adjustedMaxReach) {
            double reachExcess = actualReach - adjustedMaxReach;
            double reachAnomaly = reachExcess / adjustedMaxReach;
            anomalyScore += reachAnomaly * 3.0; // Strong signal
            explain.put("reachExcess", reachExcess);
            explain.put("directReachAnomaly", reachAnomaly);
        }
        
        // === Analysis 2: Horizontal Reach Check ===
        // Sometimes players abuse vertical reach more than horizontal
        if (horizontalReach > VANILLA_REACH + pingCompensation + 0.5) {
            double hReachExcess = horizontalReach - (VANILLA_REACH + pingCompensation);
            double hReachAnomaly = hReachExcess / VANILLA_REACH;
            anomalyScore += hReachAnomaly * 2.0;
            explain.put("horizontalReachExcess", hReachExcess);
        }
        
        // === Analysis 3: Statistical Reach Analysis ===
        // Analyze reach patterns over time
        if (context.getReachWindow().size() >= minSamplesRequired) {
            double medianReach = context.getReachWindow().median();
            double reachMad = context.getReachWindow().mad();
            double maxRecordedReach = context.getReachWindow().max();
            
            // Check if consistently hitting at max range
            if (medianReach > VANILLA_REACH - 0.3 && reachMad < 0.3) {
                // Always hitting at edge of reach - suspicious
                double consistentMaxReachAnomaly = (medianReach - (VANILLA_REACH - 0.5)) / 0.5;
                if (consistentMaxReachAnomaly > 0) {
                    anomalyScore += consistentMaxReachAnomaly * 0.5;
                    explain.put("consistentMaxReach", true);
                    explain.put("medianReach", medianReach);
                }
            }
            
            // Check for any recorded reach violations
            if (maxRecordedReach > adjustedMaxReach) {
                double maxReachAnomaly = (maxRecordedReach - adjustedMaxReach) / adjustedMaxReach;
                anomalyScore += maxReachAnomaly;
                explain.put("maxRecordedReach", maxRecordedReach);
            }
            
            explain.put("reachMad", reachMad);
        }
        
        // === Analysis 4: Y-Level Reach Abuse ===
        // Detect hitting players at impossible Y offsets
        double yDiff = Math.abs(input.targetY() - input.attackerY());
        if (yDiff > 2.0 && actualReach > VANILLA_REACH) {
            // Hitting at significant height difference with extended reach
            double yReachAnomaly = (yDiff - 2.0) * (actualReach - VANILLA_REACH);
            if (yReachAnomaly > 0) {
                anomalyScore += yReachAnomaly * 0.3;
                explain.put("yDifference", yDiff);
                explain.put("yReachAnomaly", yReachAnomaly);
            }
        }
        
        // === Analysis 5: Movement Compensation ===
        // Check if target was moving away and still got hit at extended range
        // (This would require target velocity data - placeholder for future)
        
        // Convert to confidence using sigmoid transformation
        double confidence = Stats.anomalyToConfidence(anomalyScore, SCALE_FACTOR);
        double severity = Math.min(1.0, anomalyScore / 3.0);
        
        if (confidence < 0.1) {
            return CombatCheckResult.clean(NAME);
        }
        
        explain.put("anomalyScore", anomalyScore);
        explain.put("actualReach", actualReach);
        explain.put("horizontalReach", horizontalReach);
        explain.put("pingCompensation", pingCompensation);
        explain.put("adjustedMaxReach", adjustedMaxReach);
        
        return CombatCheckResult.violation(NAME, confidence, severity, explain);
    }
}
