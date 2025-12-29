package com.macmoment.macac.pipeline.checks;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CheckResult;
import com.macmoment.macac.model.Features;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.TelemetryInput;
import com.macmoment.macac.pipeline.Check;
import com.macmoment.macac.util.RingBuffer;
import com.macmoment.macac.util.Stats;

import java.util.HashMap;
import java.util.Map;

/**
 * Predicts player movement based on recent history and flags persistent drift.
 * Requires sustained evidence across multiple samples to avoid false positives.
 */
public final class PredictionDriftCheck implements Check {
    
    private static final String NAME = "PredictionDrift";
    private static final String CATEGORY = "movement";
    private static final double SCALE_FACTOR = 2.0;
    
    // Configuration
    private boolean enabled;
    private double weight;
    private int minDriftSamples;
    private double maxDriftThreshold;
    
    // Prediction state (per-player tracking handled via context)
    // We use a simple linear extrapolation model

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
        this.enabled = config.isPredictionDriftEnabled();
        this.weight = config.getPredictionDriftWeight();
        this.minDriftSamples = config.getMinDriftSamples();
        this.maxDriftThreshold = config.getMaxDriftThreshold();
    }

    @Override
    public CheckResult analyze(TelemetryInput input, Features features, PlayerContext context) {
        if (!enabled) {
            return CheckResult.clean(NAME);
        }
        
        // Need sufficient history for prediction
        RingBuffer<TelemetryInput> history = context.getTelemetryHistory();
        if (history.size() < minDriftSamples + 2) {
            return CheckResult.clean(NAME);
        }
        
        // Skip if player has special movement states
        if (input.hasSpecialMovement()) {
            return CheckResult.clean(NAME);
        }
        
        // Build prediction based on recent velocity trend
        // Use last N samples to compute average velocity and predict current position
        double totalDx = 0, totalDy = 0, totalDz = 0;
        int sampleCount = 0;
        
        for (int i = 1; i <= minDriftSamples && i < history.size(); i++) {
            TelemetryInput sample = history.get(i);
            if (sample != null) {
                totalDx += sample.dx();
                totalDy += sample.dy();
                totalDz += sample.dz();
                sampleCount++;
            }
        }
        
        if (sampleCount < minDriftSamples - 1) {
            return CheckResult.clean(NAME);
        }
        
        // Average velocity from history
        double avgDx = totalDx / sampleCount;
        double avgDy = totalDy / sampleCount;
        double avgDz = totalDz / sampleCount;
        
        // Predicted delta for this tick (simple continuation of average velocity)
        // Account for gravity on Y
        double predictedDx = avgDx;
        double predictedDy = avgDy - 0.08; // Apply gravity
        double predictedDz = avgDz;
        
        // Calculate drift from prediction
        double driftX = Math.abs(input.dx() - predictedDx);
        double driftY = Math.abs(input.dy() - predictedDy);
        double driftZ = Math.abs(input.dz() - predictedDz);
        double totalDrift = Math.sqrt(driftX * driftX + driftY * driftY + driftZ * driftZ);
        
        // Ping-adjusted threshold
        double pingFactor = 1.0 + (context.getMedianPing() / 300.0);
        double adjustedThreshold = maxDriftThreshold * pingFactor;
        
        // Check if this drift is significant
        if (totalDrift <= adjustedThreshold) {
            return CheckResult.clean(NAME);
        }
        
        // Check for sustained drift pattern
        // Look at recent drifts to see if this is consistent
        int consecutiveDrifts = countConsecutiveDrifts(context, adjustedThreshold);
        
        // Require multiple consecutive drifts before flagging
        if (consecutiveDrifts < minDriftSamples) {
            return CheckResult.clean(NAME);
        }
        
        // Calculate anomaly score based on drift magnitude and consistency
        double driftExcess = (totalDrift - adjustedThreshold) / adjustedThreshold;
        double consistencyBonus = (consecutiveDrifts - minDriftSamples) * 0.2;
        double anomalyScore = driftExcess + consistencyBonus;
        
        // Convert to confidence
        double confidence = Stats.anomalyToConfidence(anomalyScore, SCALE_FACTOR);
        double severity = Math.min(1.0, anomalyScore / 2.0);
        
        if (confidence < 0.1) {
            return CheckResult.clean(NAME);
        }
        
        Map<String, Object> explain = new HashMap<>();
        explain.put("totalDrift", totalDrift);
        explain.put("predictedDx", predictedDx);
        explain.put("predictedDy", predictedDy);
        explain.put("predictedDz", predictedDz);
        explain.put("actualDx", input.dx());
        explain.put("actualDy", input.dy());
        explain.put("actualDz", input.dz());
        explain.put("consecutiveDrifts", consecutiveDrifts);
        explain.put("threshold", adjustedThreshold);
        explain.put("anomalyScore", anomalyScore);
        
        return CheckResult.violation(NAME, confidence, severity, explain);
    }

    /**
     * Counts consecutive samples with drift above threshold.
     */
    private int countConsecutiveDrifts(PlayerContext context, double threshold) {
        RingBuffer<TelemetryInput> history = context.getTelemetryHistory();
        int count = 0;
        
        // Start from most recent and work back
        TelemetryInput prev = null;
        for (int i = 0; i < history.size() && i < minDriftSamples * 2; i++) {
            TelemetryInput current = history.get(i);
            if (current == null) break;
            
            if (prev != null) {
                // Simple drift calculation between consecutive samples
                double drift = Math.sqrt(
                    Math.pow(current.dx() - prev.dx(), 2) +
                    Math.pow(current.dy() - prev.dy() + 0.08, 2) + // Gravity adjusted
                    Math.pow(current.dz() - prev.dz(), 2)
                );
                
                if (drift > threshold * 0.5) { // More lenient for history check
                    count++;
                } else {
                    break; // Streak broken
                }
            }
            prev = current;
        }
        
        return count;
    }
}
