package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.Features;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.TelemetryInput;

/**
 * Extracts features from raw telemetry input.
 * Computes derived metrics like speed, acceleration, and jitter.
 */
public final class FeatureExtractor {
    
    private static final double NANOS_PER_TICK = 50_000_000.0; // 50ms
    
    /**
     * Extracts features from the current input and player context.
     * 
     * @param input Current telemetry input
     * @param context Player context with history
     * @return Extracted features
     */
    public Features extract(TelemetryInput input, PlayerContext context) {
        Features.Builder builder = Features.builder();
        
        // Basic speed calculations
        double horizSpeed = input.horizontalSpeed();
        double vertSpeed = input.dy();
        double speed3D = input.speed3D();
        
        builder.horizSpeed(horizSpeed)
               .vertSpeed(vertSpeed)
               .speed3D(speed3D);
        
        // Acceleration (requires history)
        double horizAccel = 0.0;
        double vertAccel = 0.0;
        
        Features prevFeatures = context.getFeatureHistory().peek();
        if (prevFeatures != null) {
            horizAccel = horizSpeed - prevFeatures.horizSpeed();
            vertAccel = vertSpeed - prevFeatures.vertSpeed();
        }
        
        builder.horizAccel(horizAccel)
               .vertAccel(vertAccel);
        
        // Rotation metrics
        double rotationSpeed = Math.sqrt(
            input.deltaYaw() * input.deltaYaw() + 
            input.deltaPitch() * input.deltaPitch()
        );
        
        double yawAccel = 0.0;
        double pitchAccel = 0.0;
        
        TelemetryInput prevTelemetry = context.getTelemetryHistory().peek();
        if (prevTelemetry != null) {
            yawAccel = input.deltaYaw() - prevTelemetry.deltaYaw();
            pitchAccel = input.deltaPitch() - prevTelemetry.deltaPitch();
        }
        
        builder.rotationSpeed(rotationSpeed)
               .yawAccel(yawAccel)
               .pitchAccel(pitchAccel);
        
        // Jitter analysis
        double jitterScore = calculateJitterScore(context);
        builder.jitterScore(jitterScore);
        
        // Timing skew
        double timingSkew = calculateTimingSkew(input, context);
        builder.timingSkew(timingSkew);
        
        // Ping normalization
        long pingNormalized = normalizePing(input.ping(), context);
        builder.pingNormalized(pingNormalized);
        
        // Lag detection
        boolean isLagging = detectLag(input, context);
        builder.isLagging(isLagging);
        
        // Sample count
        builder.sampleCount(context.getTelemetryHistory().size());
        
        return builder.build();
    }
    
    /**
     * Calculates jitter score based on movement irregularity.
     */
    private double calculateJitterScore(PlayerContext context) {
        if (context.getTelemetryHistory().size() < 5) {
            return 0.0;
        }
        
        // Calculate variance of movement deltas
        double sumSqDiff = 0.0;
        double prevHorizSpeed = 0.0;
        int count = 0;
        
        for (TelemetryInput input : context.getTelemetryHistory()) {
            if (input == null) continue;
            
            double horizSpeed = input.horizontalSpeed();
            if (count > 0) {
                double diff = horizSpeed - prevHorizSpeed;
                sumSqDiff += diff * diff;
            }
            prevHorizSpeed = horizSpeed;
            count++;
            
            if (count >= 10) break; // Limit to recent samples
        }
        
        return count > 1 ? Math.sqrt(sumSqDiff / count) : 0.0;
    }
    
    /**
     * Calculates timing skew from expected tick interval.
     */
    private double calculateTimingSkew(TelemetryInput input, PlayerContext context) {
        if (context.getPacketDeltaWindow().isEmpty()) {
            return 0.0;
        }
        
        double medianDelta = context.getPacketDeltaWindow().median();
        double expectedDelta = 50.0; // 50ms per tick
        
        // Adjust expected delta based on ping
        double pingAdjustment = context.getMedianPing() * 0.02;
        expectedDelta += pingAdjustment;
        
        return Math.abs(medianDelta - expectedDelta) / expectedDelta;
    }
    
    /**
     * Normalizes ping using EWMA or median.
     */
    private long normalizePing(long rawPing, PlayerContext context) {
        if (!context.getPingEwma().isInitialized()) {
            return rawPing;
        }
        
        // Use EWMA-smoothed ping
        return (long) context.getPingEwma().get();
    }
    
    /**
     * Detects if player appears to be lagging.
     */
    private boolean detectLag(TelemetryInput input, PlayerContext context) {
        if (context.getPacketDeltaWindow().size() < 3) {
            return false;
        }
        
        // Check for sudden ping spike
        double pingMad = context.getPingMad();
        double medianPing = context.getMedianPing();
        
        if (pingMad > 0 && input.ping() > medianPing + (pingMad * 3)) {
            return true;
        }
        
        // Check for large tick delta (missed packets)
        if (input.tickDelta() > 200_000_000) { // > 200ms
            return true;
        }
        
        return false;
    }
}
