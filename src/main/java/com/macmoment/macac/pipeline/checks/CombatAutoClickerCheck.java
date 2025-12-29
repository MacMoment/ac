package com.macmoment.macac.pipeline.checks;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CombatCheckResult;
import com.macmoment.macac.model.CombatContext;
import com.macmoment.macac.model.CombatInput;
import com.macmoment.macac.util.Stats;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects killaura and auto-clicker by analyzing attack patterns and hit rates.
 * 
 * Detection signals:
 * - Unnaturally high hit rates (normal ~60-70%, aura often 90%+)
 * - Consistent attack intervals (auto-clickers)
 * - Attacks while looking away from target
 * - Hitting multiple targets in quick succession
 * - Attack speed violations (exceeding cooldown)
 * 
 * Uses native RDTSCP timing for precise attack interval analysis.
 */
public final class CombatAutoClickerCheck {
    
    private static final String NAME = "CombatAutoClicker";
    private static final String CATEGORY = "combat";
    private static final double SCALE_FACTOR = 1.8;
    
    // Configuration
    private boolean enabled;
    private double weight;
    private double maxHitRate;             // Maximum suspicious hit rate
    private double minAttackInterval;      // Minimum ms between attacks (attack speed)
    private double maxIntervalConsistency; // Maximum variance in attack intervals
    private int minSamplesRequired;        // Minimum samples before analysis
    
    // Attack cooldown constants (Minecraft 1.9+ combat)
    private static final double ATTACK_COOLDOWN_MS = 625.0;  // Sword full charge time
    private static final double MIN_HUMAN_CPS = 6.0;         // Clicks per second
    private static final double MAX_HUMAN_CPS = 15.0;        // Peak human CPS
    private static final double SUSPICIOUS_CPS = 20.0;       // Auto-clicker territory
    
    // Human hit rate statistics
    private static final double HUMAN_AVERAGE_HIT_RATE = 0.65;
    private static final double HUMAN_MAX_HIT_RATE = 0.85;
    private static final double AIMBOT_HIT_RATE_THRESHOLD = 0.90;

    public String getName() { return NAME; }
    public String getCategory() { return CATEGORY; }
    public boolean isEnabled() { return enabled; }
    public double getWeight() { return weight; }
    
    public void configure(EngineConfig config) {
        this.enabled = config.isCombatAutoClickerEnabled();
        this.weight = config.getCombatAutoClickerWeight();
        this.maxHitRate = config.getCombatMaxHitRate();
        this.minAttackInterval = config.getCombatMinAttackInterval();
        this.maxIntervalConsistency = config.getCombatMaxIntervalConsistency();
        this.minSamplesRequired = config.getCombatMinSamples();
    }
    
    /**
     * Analyzes combat patterns for auto-clicker and killaura behavior.
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
        if (context.getCombatHistory().size() < minSamplesRequired) {
            return CombatCheckResult.clean(NAME);
        }
        
        double anomalyScore = 0.0;
        Map<String, Object> explain = new HashMap<>();
        
        // === Analysis 1: Hit Rate Detection ===
        // Suspiciously high hit rates indicate aimbot/killaura
        double recentHitRate = context.getRecentHitRate();
        double totalHitRate = context.getTotalHitRate();
        
        if (recentHitRate > maxHitRate && context.getHitRateWindow().size() >= minSamplesRequired) {
            double hitRateExcess = recentHitRate - maxHitRate;
            double hitRateAnomaly = hitRateExcess / (1.0 - maxHitRate);
            anomalyScore += hitRateAnomaly * 2.0;
            explain.put("recentHitRate", recentHitRate);
            explain.put("hitRateAnomaly", hitRateAnomaly);
        }
        
        // Extra suspicious if maintaining very high hit rate over time
        if (totalHitRate > AIMBOT_HIT_RATE_THRESHOLD && context.getTotalAttacks() >= minSamplesRequired * 3) {
            double sustainedAnomaly = (totalHitRate - AIMBOT_HIT_RATE_THRESHOLD) * 2.0;
            anomalyScore += sustainedAnomaly;
            explain.put("totalHitRate", totalHitRate);
            explain.put("sustainedHighHitRate", true);
        }
        
        // === Analysis 2: Attack Speed/CPS Analysis ===
        // Detect auto-clickers by analyzing click patterns
        if (context.getAttackIntervalWindow().size() >= minSamplesRequired) {
            double meanInterval = context.getAttackIntervalWindow().mean();
            double intervalStdDev = context.getAttackIntervalWindow().stdDev();
            
            // Calculate effective CPS
            double cps = meanInterval > 0 ? 1000.0 / meanInterval : 0;
            
            // Check for impossibly fast clicking
            if (cps > SUSPICIOUS_CPS) {
                double cpsAnomaly = (cps - SUSPICIOUS_CPS) / SUSPICIOUS_CPS;
                anomalyScore += cpsAnomaly * 2.5;
                explain.put("cps", cps);
                explain.put("cpsAnomaly", cpsAnomaly);
            }
            
            // Check attack cooldown violations
            double minInterval = context.getAttackIntervalWindow().min();
            if (minInterval < minAttackInterval) {
                double cooldownViolation = (minAttackInterval - minInterval) / minAttackInterval;
                anomalyScore += cooldownViolation;
                explain.put("minInterval", minInterval);
                explain.put("cooldownViolation", cooldownViolation);
            }
            
            explain.put("meanAttackInterval", meanInterval);
        }
        
        // === Analysis 3: Click Pattern Consistency ===
        // Auto-clickers have unnaturally consistent intervals
        if (context.getAttackIntervalWindow().size() >= minSamplesRequired * 2) {
            double intervalMad = context.getAttackIntervalWindow().mad();
            double meanInterval = context.getAttackIntervalWindow().mean();
            
            // Coefficient of variation for intervals
            double intervalCV = meanInterval > 0 ? intervalMad / meanInterval : 0;
            
            if (intervalCV < maxIntervalConsistency) {
                // Too consistent - machine-like clicking
                double consistencyAnomaly = (maxIntervalConsistency - intervalCV) / maxIntervalConsistency;
                anomalyScore += consistencyAnomaly * 1.5;
                explain.put("intervalConsistency", intervalCV);
                explain.put("consistencyAnomaly", consistencyAnomaly);
            }
        }
        
        // === Analysis 4: Look-Away Attacks ===
        // Killaura can hit targets not in FOV
        double aimError = input.aimError();
        if (input.hit() && aimError > 90.0) {
            // Hit something behind them - major violation
            double lookAwayAnomaly = (aimError - 90.0) / 90.0;
            anomalyScore += lookAwayAnomaly * 3.0;
            explain.put("aimError", aimError);
            explain.put("lookAwayHit", true);
        } else if (input.hit() && aimError > 45.0) {
            // Hit at edge of FOV - minor flag
            double edgeHitAnomaly = (aimError - 45.0) / 45.0;
            anomalyScore += edgeHitAnomaly * 0.5;
            explain.put("edgeAimError", aimError);
        }
        
        // === Analysis 5: Multi-Target Rapid Switching ===
        // Killaura rapidly switches between targets
        if (context.getCombatHistory().size() >= 3) {
            int targetSwitches = countRecentTargetSwitches(context, 5);
            int quickSwitches = countQuickTargetSwitches(context, 5, 500); // 500ms threshold
            
            if (quickSwitches >= 3) {
                double multiTargetAnomaly = quickSwitches * 0.3;
                anomalyScore += multiTargetAnomaly;
                explain.put("quickTargetSwitches", quickSwitches);
            }
        }
        
        // === Analysis 6: Critical Hit Rate ===
        // Suspiciously high critical rate can indicate hack
        double critRate = context.getCriticalRate();
        if (critRate > 0.7 && context.getTotalHits() >= minSamplesRequired) {
            // Normal crit rate is ~20-30% for skilled players
            double critAnomaly = (critRate - 0.5) * 1.5;
            if (critAnomaly > 0) {
                anomalyScore += critAnomaly;
                explain.put("criticalRate", critRate);
            }
        }
        
        // Convert to confidence using sigmoid transformation
        double confidence = Stats.anomalyToConfidence(anomalyScore, SCALE_FACTOR);
        double severity = Math.min(1.0, anomalyScore / 3.0);
        
        if (confidence < 0.1) {
            return CombatCheckResult.clean(NAME);
        }
        
        explain.put("anomalyScore", anomalyScore);
        explain.put("totalAttacks", context.getTotalAttacks());
        
        return CombatCheckResult.violation(NAME, confidence, severity, explain);
    }
    
    /**
     * Counts recent target switches in combat history.
     */
    private int countRecentTargetSwitches(CombatContext context, int lookback) {
        int switches = 0;
        java.util.UUID lastTarget = null;
        
        int count = Math.min(lookback, context.getCombatHistory().size());
        for (int i = 0; i < count; i++) {
            CombatInput ci = context.getCombatHistory().get(i);
            if (ci != null && ci.targetId() != null) {
                if (lastTarget != null && !ci.targetId().equals(lastTarget)) {
                    switches++;
                }
                lastTarget = ci.targetId();
            }
        }
        return switches;
    }
    
    /**
     * Counts target switches that happened very quickly.
     */
    private int countQuickTargetSwitches(CombatContext context, int lookback, long thresholdMs) {
        int quickSwitches = 0;
        java.util.UUID lastTarget = null;
        long lastTime = 0;
        
        int count = Math.min(lookback, context.getCombatHistory().size());
        for (int i = 0; i < count; i++) {
            CombatInput ci = context.getCombatHistory().get(i);
            if (ci != null && ci.targetId() != null) {
                if (lastTarget != null && !ci.targetId().equals(lastTarget)) {
                    long timeDiff = Math.abs(ci.nanoTime() - lastTime) / 1_000_000;
                    if (timeDiff < thresholdMs) {
                        quickSwitches++;
                    }
                }
                lastTarget = ci.targetId();
                lastTime = ci.nanoTime();
            }
        }
        return quickSwitches;
    }
}
