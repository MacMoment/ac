package com.macmoment.macac.model;

import java.util.Objects;

/**
 * Extracted features from telemetry data for check analysis.
 * 
 * <p>Features are derived metrics computed from raw telemetry input and
 * player history. They provide normalized, analysis-ready values that
 * checks can use for detection logic.
 * 
 * <p><strong>Feature Categories:</strong>
 * <ul>
 *   <li><strong>Speed metrics</strong>: horizontal, vertical, and 3D velocity</li>
 *   <li><strong>Acceleration</strong>: rate of speed change in each axis</li>
 *   <li><strong>Rotation metrics</strong>: aim speed and acceleration</li>
 *   <li><strong>Jitter analysis</strong>: movement irregularity detection</li>
 *   <li><strong>Timing</strong>: packet timing deviation from expected</li>
 *   <li><strong>Context</strong>: lag detection and sample count</li>
 * </ul>
 * 
 * <p>All speed values are in blocks per tick (50ms). Acceleration values
 * represent the change in speed between consecutive ticks.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
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
     * Creates a builder for constructing Features instances.
     * 
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Returns true if movement is above typical walking speed.
     * 
     * @return true if horizontal speed exceeds normal walking
     */
    public boolean isMovingFast() {
        return horizSpeed > 0.3; // ~walking speed threshold
    }
    
    /**
     * Returns true if any significant rotation occurred.
     * 
     * @return true if rotation speed is above threshold
     */
    public boolean isRotating() {
        return rotationSpeed > 0.1;
    }

    /**
     * Builder for constructing Features instances with fluent API.
     * 
     * <p>All fields default to zero/false and can be set individually.
     */
    public static final class Builder {
        private double horizSpeed;
        private double vertSpeed;
        private double speed3D;
        private double horizAccel;
        private double vertAccel;
        private double rotationSpeed;
        private double yawAccel;
        private double pitchAccel;
        private double jitterScore;
        private double timingSkew;
        private long pingNormalized;
        private boolean isLagging;
        private long sampleCount;

        /**
         * Sets the horizontal speed.
         * @param horizSpeed horizontal speed in blocks/tick
         * @return this builder
         */
        public Builder horizSpeed(final double horizSpeed) { 
            this.horizSpeed = horizSpeed; 
            return this; 
        }
        
        /**
         * Sets the vertical speed.
         * @param vertSpeed vertical speed in blocks/tick
         * @return this builder
         */
        public Builder vertSpeed(final double vertSpeed) { 
            this.vertSpeed = vertSpeed; 
            return this; 
        }
        
        /**
         * Sets the 3D speed magnitude.
         * @param speed3D 3D speed in blocks/tick
         * @return this builder
         */
        public Builder speed3D(final double speed3D) { 
            this.speed3D = speed3D; 
            return this; 
        }
        
        /**
         * Sets the horizontal acceleration.
         * @param horizAccel change in horizontal speed
         * @return this builder
         */
        public Builder horizAccel(final double horizAccel) { 
            this.horizAccel = horizAccel; 
            return this; 
        }
        
        /**
         * Sets the vertical acceleration.
         * @param vertAccel change in vertical speed
         * @return this builder
         */
        public Builder vertAccel(final double vertAccel) { 
            this.vertAccel = vertAccel; 
            return this; 
        }
        
        /**
         * Sets the rotation speed.
         * @param rotationSpeed combined rotation magnitude
         * @return this builder
         */
        public Builder rotationSpeed(final double rotationSpeed) { 
            this.rotationSpeed = rotationSpeed; 
            return this; 
        }
        
        /**
         * Sets the yaw acceleration.
         * @param yawAccel change in yaw rotation speed
         * @return this builder
         */
        public Builder yawAccel(final double yawAccel) { 
            this.yawAccel = yawAccel; 
            return this; 
        }
        
        /**
         * Sets the pitch acceleration.
         * @param pitchAccel change in pitch rotation speed
         * @return this builder
         */
        public Builder pitchAccel(final double pitchAccel) { 
            this.pitchAccel = pitchAccel; 
            return this; 
        }
        
        /**
         * Sets the jitter score.
         * @param jitterScore movement irregularity measure
         * @return this builder
         */
        public Builder jitterScore(final double jitterScore) { 
            this.jitterScore = jitterScore; 
            return this; 
        }
        
        /**
         * Sets the timing skew.
         * @param timingSkew deviation from expected packet timing
         * @return this builder
         */
        public Builder timingSkew(final double timingSkew) { 
            this.timingSkew = timingSkew; 
            return this; 
        }
        
        /**
         * Sets the normalized ping.
         * @param pingNormalized smoothed ping value in milliseconds
         * @return this builder
         */
        public Builder pingNormalized(final long pingNormalized) { 
            this.pingNormalized = pingNormalized; 
            return this; 
        }
        
        /**
         * Sets the lagging flag.
         * @param isLagging true if player appears to be lagging
         * @return this builder
         */
        public Builder isLagging(final boolean isLagging) { 
            this.isLagging = isLagging; 
            return this; 
        }
        
        /**
         * Sets the sample count.
         * @param sampleCount number of samples in history
         * @return this builder
         */
        public Builder sampleCount(final long sampleCount) { 
            this.sampleCount = sampleCount; 
            return this; 
        }

        /**
         * Builds an immutable Features instance.
         * 
         * @return new Features with configured values
         */
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
