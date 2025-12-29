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
 * Analyzes packet inter-arrival times to detect unnatural burst patterns.
 * Uses ping-normalized timing with rolling median and MAD for robust detection.
 */
public final class PacketTimingCheck implements Check {
    
    private static final String NAME = "PacketTiming";
    private static final String CATEGORY = "timing";
    
    // Configuration
    private boolean enabled;
    private double weight;
    private long minDeltaMs;
    private double maxJitterCoeff;
    
    // Expected tick interval (50ms for 20 TPS)
    private static final double EXPECTED_DELTA_MS = 50.0;
    private static final double SCALE_FACTOR = 2.0;

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
        this.enabled = config.isPacketTimingEnabled();
        this.weight = config.getPacketTimingWeight();
        this.minDeltaMs = config.getPacketTimingMinDeltaMs();
        this.maxJitterCoeff = config.getPacketTimingMaxJitter();
    }

    @Override
    public CheckResult analyze(TelemetryInput input, Features features, PlayerContext context) {
        if (!enabled) {
            return CheckResult.clean(NAME);
        }
        
        // Need enough history for meaningful analysis
        if (context.getPacketDeltaWindow().size() < 5) {
            return CheckResult.clean(NAME);
        }
        
        // Skip if player has special movement states
        if (input.hasSpecialMovement()) {
            return CheckResult.clean(NAME);
        }
        
        // Get timing statistics
        double medianDelta = context.getPacketDeltaWindow().median();
        double madDelta = context.getPacketDeltaWindow().mad();
        
        // Protect against zero MAD (all identical values)
        if (madDelta < 0.1) {
            madDelta = 0.1;
        }
        
        // Check for suspiciously consistent timing (too perfect)
        // Normal players have some variation; cheats often have machine-like precision
        double consistencyAnomaly = 0.0;
        if (madDelta < 1.0 && context.getPacketDeltaWindow().size() >= 10) {
            // Very low variance is suspicious
            consistencyAnomaly = 1.0 - madDelta;
        }
        
        // Check for burst patterns (many packets in short time)
        double[] deltas = context.getPacketDeltaWindow().toArray();
        int burstCount = 0;
        for (double delta : deltas) {
            if (delta < minDeltaMs) {
                burstCount++;
            }
        }
        double burstRatio = (double) burstCount / deltas.length;
        
        // Calculate timing skew from expected interval
        // Account for ping in expected interval
        double pingAdjustedExpected = EXPECTED_DELTA_MS + (context.getMedianPing() * 0.05);
        double skew = Math.abs(medianDelta - pingAdjustedExpected) / pingAdjustedExpected;
        
        // Check for jitter coefficient (stdDev / mean)
        double mean = context.getPacketDeltaWindow().mean();
        double stdDev = context.getPacketDeltaWindow().stdDev();
        double jitterCoeff = mean > 0 ? stdDev / mean : 0;
        
        // Combine anomaly signals
        double anomalyScore = 0.0;
        
        // Burst detection
        if (burstRatio > 0.3) {
            anomalyScore += burstRatio * 2.0;
        }
        
        // Consistency anomaly (too perfect timing)
        if (consistencyAnomaly > 0.5) {
            anomalyScore += consistencyAnomaly;
        }
        
        // Excessive jitter
        if (jitterCoeff > maxJitterCoeff) {
            anomalyScore += (jitterCoeff - maxJitterCoeff) / maxJitterCoeff;
        }
        
        // High skew from expected
        if (skew > 0.5) {
            anomalyScore += skew;
        }
        
        // Convert to confidence using sigmoid transformation
        double confidence = Stats.anomalyToConfidence(anomalyScore, SCALE_FACTOR);
        
        // Severity based on how many signals triggered
        double severity = Math.min(1.0, anomalyScore / 3.0);
        
        if (confidence < 0.1) {
            return CheckResult.clean(NAME);
        }
        
        Map<String, Object> explain = new HashMap<>();
        explain.put("medianDelta", medianDelta);
        explain.put("madDelta", madDelta);
        explain.put("burstRatio", burstRatio);
        explain.put("jitterCoeff", jitterCoeff);
        explain.put("skew", skew);
        explain.put("anomalyScore", anomalyScore);
        
        return CheckResult.violation(NAME, confidence, severity, explain);
    }
}
