package com.macmoment.macac.model;

/**
 * Extracted features from telemetry data for check analysis.
 * These are derived metrics computed from raw telemetry.
 */
public record Features(
    // Speed metrics
    double horizSpeed,          // Horizontal speed (blocks/tick)
    double vertSpeed,           // Vertical speed (blocks/tick)
    double speed3D,             // 3D speed magnitude
    
    // Acceleration
    double horizAccel,          // Horizontal acceleration (change in speed)
    double vertAccel,           // Vertical acceleration
    
    // Rotation metrics
    double rotationSpeed,       // Combined rotation speed
    double yawAccel,            // Yaw acceleration
    double pitchAccel,          // Pitch acceleration
    
    // Jitter analysis
    double jitterScore,         // Measure of movement irregularity
    double timingSkew,          // Deviation from expected packet timing
    
    // Context
    long pingNormalized,        // Ping normalized for analysis
    boolean isLagging,          // True if player appears to be lagging
    long sampleCount            // Number of samples in history
) {
    /**
     * Creates a builder for Features.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Features.
     */
    public static class Builder {
        private double horizSpeed, vertSpeed, speed3D;
        private double horizAccel, vertAccel;
        private double rotationSpeed, yawAccel, pitchAccel;
        private double jitterScore, timingSkew;
        private long pingNormalized;
        private boolean isLagging;
        private long sampleCount;

        public Builder horizSpeed(double horizSpeed) { this.horizSpeed = horizSpeed; return this; }
        public Builder vertSpeed(double vertSpeed) { this.vertSpeed = vertSpeed; return this; }
        public Builder speed3D(double speed3D) { this.speed3D = speed3D; return this; }
        public Builder horizAccel(double horizAccel) { this.horizAccel = horizAccel; return this; }
        public Builder vertAccel(double vertAccel) { this.vertAccel = vertAccel; return this; }
        public Builder rotationSpeed(double rotationSpeed) { this.rotationSpeed = rotationSpeed; return this; }
        public Builder yawAccel(double yawAccel) { this.yawAccel = yawAccel; return this; }
        public Builder pitchAccel(double pitchAccel) { this.pitchAccel = pitchAccel; return this; }
        public Builder jitterScore(double jitterScore) { this.jitterScore = jitterScore; return this; }
        public Builder timingSkew(double timingSkew) { this.timingSkew = timingSkew; return this; }
        public Builder pingNormalized(long pingNormalized) { this.pingNormalized = pingNormalized; return this; }
        public Builder isLagging(boolean isLagging) { this.isLagging = isLagging; return this; }
        public Builder sampleCount(long sampleCount) { this.sampleCount = sampleCount; return this; }

        public Features build() {
            return new Features(
                horizSpeed, vertSpeed, speed3D,
                horizAccel, vertAccel,
                rotationSpeed, yawAccel, pitchAccel,
                jitterScore, timingSkew,
                pingNormalized, isLagging, sampleCount
            );
        }
    }
}
