package com.macmoment.macac.util;

import java.util.Arrays;
import java.util.Objects;

/**
 * Statistical utility methods for anti-cheat analysis.
 * 
 * <p>This class provides robust statistical measures that are resistant to outliers,
 * making them suitable for analyzing potentially adversarial player data. All methods
 * are designed to be null-safe and return sensible defaults (typically 0.0) for
 * empty or null inputs.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Median and MAD (Median Absolute Deviation) for robust central tendency</li>
 *   <li>EWMA (Exponentially Weighted Moving Average) for streaming data</li>
 *   <li>Confidence fusion methods for multi-signal aggregation</li>
 *   <li>Sigmoid transformation for anomaly-to-confidence conversion</li>
 * </ul>
 * 
 * <p>Thread Safety: Static methods are thread-safe. Mutable inner classes
 * ({@link EWMA}, {@link RollingWindow}) are NOT thread-safe and should be
 * confined to a single thread or protected externally.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class Stats {

    /** Minimum alpha value for EWMA (exclusive lower bound). */
    private static final double EWMA_ALPHA_MIN = 0.0;
    
    /** Maximum alpha value for EWMA (inclusive upper bound). */
    private static final double EWMA_ALPHA_MAX = 1.0;
    
    /** Default return value for empty/null statistical computations. */
    private static final double EMPTY_RESULT = 0.0;

    /**
     * Private constructor to prevent instantiation.
     * 
     * @throws AssertionError always, if called via reflection
     */
    private Stats() {
        throw new AssertionError("Stats is a utility class and cannot be instantiated");
    }

    /**
     * Calculates the median of an array of values.
     * 
     * <p>The median is the middle value when sorted, or the average of the two
     * middle values for even-length arrays. This implementation creates a sorted
     * copy and does not modify the input array.
     * 
     * @param values array of values; may be null or empty
     * @return median value, or {@value #EMPTY_RESULT} if input is null or empty
     */
    public static double median(final double[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_RESULT;
        }
        
        final double[] sorted = values.clone();
        Arrays.sort(sorted);
        
        final int length = sorted.length;
        final int mid = length / 2;
        
        if (length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
        return sorted[mid];
    }

    /**
     * Calculates the Median Absolute Deviation (MAD).
     * 
     * <p>MAD is a robust measure of variability that is resistant to outliers.
     * It is defined as the median of the absolute deviations from the median:
     * {@code MAD = median(|x_i - median(x)|)}
     * 
     * <p>For normally distributed data, the standard deviation can be estimated
     * as approximately {@code 1.4826 * MAD}.
     * 
     * @param values array of values; may be null or empty
     * @return MAD value, or {@value #EMPTY_RESULT} if input is null or empty
     */
    public static double mad(final double[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_RESULT;
        }
        
        final double med = median(values);
        final double[] deviations = new double[values.length];
        
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - med);
        }
        
        return median(deviations);
    }

    /**
     * Calculates the arithmetic mean of an array of values.
     * 
     * @param values array of values; may be null or empty
     * @return mean value, or {@value #EMPTY_RESULT} if input is null or empty
     */
    public static double mean(final double[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_RESULT;
        }
        
        double sum = 0.0;
        for (final double v : values) {
            sum += v;
        }
        
        return sum / values.length;
    }

    /**
     * Calculates the sample standard deviation of an array of values.
     * 
     * <p>Uses Bessel's correction (n-1 denominator) for an unbiased estimate
     * of the population standard deviation from a sample.
     * 
     * @param values array of values; may be null or empty
     * @return standard deviation, or {@value #EMPTY_RESULT} if input has fewer than 2 values
     */
    public static double stdDev(final double[] values) {
        if (values == null || values.length < 2) {
            return EMPTY_RESULT;
        }
        
        final double meanValue = mean(values);
        double sumSquares = 0.0;
        
        for (final double v : values) {
            final double diff = v - meanValue;
            sumSquares += diff * diff;
        }
        
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    /**
     * Exponentially Weighted Moving Average (EWMA) calculator.
     * 
     * <p>EWMA provides a smoothed estimate that gives more weight to recent
     * observations while still considering historical data. The update formula is:
     * {@code EWMA_new = alpha * value + (1 - alpha) * EWMA_old}
     * 
     * <p>A higher alpha value makes the EWMA more reactive to new values,
     * while a lower alpha provides more smoothing.
     * 
     * <p><strong>Thread Safety:</strong> This class is NOT thread-safe.
     * External synchronization is required if accessed from multiple threads.
     * 
     * @since 1.0.0
     */
    public static final class EWMA {
        
        private final double alpha;
        private double value;
        private boolean initialized;

        /**
         * Creates a new EWMA calculator with the specified smoothing factor.
         * 
         * @param alpha smoothing factor in range (0.0, 1.0]; higher = more reactive
         * @throws IllegalArgumentException if alpha is not in valid range
         */
        public EWMA(final double alpha) {
            if (alpha <= EWMA_ALPHA_MIN || alpha > EWMA_ALPHA_MAX) {
                throw new IllegalArgumentException(
                    String.format("Alpha must be in range (%.1f, %.1f], got: %.6f",
                        EWMA_ALPHA_MIN, EWMA_ALPHA_MAX, alpha));
            }
            this.alpha = alpha;
            this.value = EMPTY_RESULT;
            this.initialized = false;
        }

        /**
         * Updates the EWMA with a new observation.
         * 
         * <p>The first update initializes the EWMA to the provided value.
         * Subsequent updates apply the exponential smoothing formula.
         * 
         * @param newValue new observation
         * @return updated EWMA value
         */
        public double update(final double newValue) {
            if (!initialized) {
                value = newValue;
                initialized = true;
            } else {
                value = alpha * newValue + (1.0 - alpha) * value;
            }
            return value;
        }

        /**
         * Returns the current EWMA value.
         * 
         * @return current EWMA, or 0.0 if not yet initialized
         */
        public double get() {
            return value;
        }

        /**
         * Returns whether at least one value has been added.
         * 
         * @return true if initialized with at least one observation
         */
        public boolean isInitialized() {
            return initialized;
        }

        /**
         * Resets the EWMA to uninitialized state.
         * 
         * <p>After reset, the next update will initialize the EWMA
         * to the provided value.
         */
        public void reset() {
            value = EMPTY_RESULT;
            initialized = false;
        }
        
        /**
         * Returns the alpha (smoothing factor) for this EWMA.
         * 
         * @return alpha value
         */
        public double getAlpha() {
            return alpha;
        }
    }

    /**
     * Rolling window for maintaining a fixed-size collection of recent values.
     * 
     * <p>Implements a circular buffer that automatically discards old values
     * when capacity is reached. Provides efficient O(1) insertion and O(n)
     * statistical computations over the windowed data.
     * 
     * <p><strong>Thread Safety:</strong> This class is NOT thread-safe.
     * External synchronization is required if accessed from multiple threads.
     * 
     * @since 1.0.0
     */
    public static final class RollingWindow {
        
        /** Minimum valid capacity for a rolling window. */
        private static final int MIN_CAPACITY = 1;
        
        private final double[] values;
        private int head;
        private int size;

        /**
         * Creates a new rolling window with the specified capacity.
         * 
         * @param capacity maximum number of values to retain; must be at least 1
         * @throws IllegalArgumentException if capacity is less than {@value #MIN_CAPACITY}
         */
        public RollingWindow(final int capacity) {
            if (capacity < MIN_CAPACITY) {
                throw new IllegalArgumentException(
                    String.format("Capacity must be at least %d, got: %d", MIN_CAPACITY, capacity));
            }
            this.values = new double[capacity];
            this.head = 0;
            this.size = 0;
        }

        /**
         * Adds a value to the window, discarding the oldest if at capacity.
         * 
         * @param value value to add
         */
        public void add(final double value) {
            values[head] = value;
            head = (head + 1) % values.length;
            if (size < values.length) {
                size++;
            }
        }

        /**
         * Returns a copy of all values currently in the window.
         * 
         * <p>Values are returned in order from oldest to newest.
         * 
         * @return defensive copy of window contents
         */
        public double[] toArray() {
            final double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                final int index = (head - size + i + values.length) % values.length;
                result[i] = values[index];
            }
            return result;
        }

        /**
         * Returns the current number of values in the window.
         * 
         * @return number of values (0 to capacity)
         */
        public int size() {
            return size;
        }

        /**
         * Returns true if the window contains no values.
         * 
         * @return true if empty
         */
        public boolean isEmpty() {
            return size == 0;
        }

        /**
         * Removes all values from the window.
         */
        public void clear() {
            head = 0;
            size = 0;
        }

        /**
         * Returns the median of values in the window.
         * 
         * @return median value, or 0.0 if empty
         */
        public double median() {
            return Stats.median(toArray());
        }

        /**
         * Returns the MAD (Median Absolute Deviation) of values in the window.
         * 
         * @return MAD value, or 0.0 if empty
         */
        public double mad() {
            return Stats.mad(toArray());
        }

        /**
         * Returns the arithmetic mean of values in the window.
         * 
         * @return mean value, or 0.0 if empty
         */
        public double mean() {
            return Stats.mean(toArray());
        }

        /**
         * Returns the sample standard deviation of values in the window.
         * 
         * @return standard deviation, or 0.0 if fewer than 2 values
         */
        public double stdDev() {
            return Stats.stdDev(toArray());
        }
        
        /**
         * Returns the minimum value in the window.
         * 
         * @return minimum value, or 0.0 if empty
         */
        public double min() {
            if (size == 0) {
                return EMPTY_RESULT;
            }
            
            double min = Double.MAX_VALUE;
            final double[] arr = toArray();
            for (final double v : arr) {
                if (v < min) {
                    min = v;
                }
            }
            return min;
        }
        
        /**
         * Returns the maximum value in the window.
         * 
         * @return maximum value, or 0.0 if empty
         */
        public double max() {
            if (size == 0) {
                return EMPTY_RESULT;
            }
            
            double max = -Double.MAX_VALUE;
            final double[] arr = toArray();
            for (final double v : arr) {
                if (v > max) {
                    max = v;
                }
            }
            return max;
        }
        
        /**
         * Returns the capacity of this window.
         * 
         * @return maximum number of values this window can hold
         */
        public int capacity() {
            return values.length;
        }
    }

    /**
     * Clamps a value to the valid confidence range [0.0, 1.0].
     * 
     * <p>Handles edge cases including NaN and infinities:
     * <ul>
     *   <li>Negative infinity and NaN → 0.0</li>
     *   <li>Positive infinity → 1.0</li>
     * </ul>
     * 
     * @param value value to bound
     * @return value clamped to [0.0, 1.0]
     */
    public static double boundConfidence(final double value) {
        if (Double.isNaN(value)) {
            return EMPTY_RESULT;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Converts an anomaly score to a confidence value using a sigmoid transformation.
     * 
     * <p>The transformation maps raw anomaly scores (which are unbounded) to the
     * valid confidence range [0.0, 1.0). Higher anomaly scores produce higher
     * confidence values, asymptotically approaching 1.0.
     * 
     * <p>The formula is: {@code 2 / (1 + e^(-score/scale)) - 1}
     * 
     * @param anomalyScore raw anomaly score (typically non-negative)
     * @param scale scaling factor controlling sigmoid sensitivity; larger = less sensitive
     * @return confidence value in range [0.0, 1.0)
     */
    public static double anomalyToConfidence(final double anomalyScore, final double scale) {
        if (anomalyScore <= 0.0 || Double.isNaN(anomalyScore)) {
            return EMPTY_RESULT;
        }
        if (scale <= 0.0) {
            return EMPTY_RESULT;
        }
        
        final double exp = Math.exp(-anomalyScore / scale);
        return boundConfidence(2.0 / (1.0 + exp) - 1.0);
    }

    /**
     * Fuses multiple confidence values using maximum (conservative approach).
     * 
     * <p>This approach is conservative in that it only requires ONE signal to
     * be confident for the fused result to be confident. Use this when any
     * single high-confidence indicator should trigger action.
     * 
     * @param confidences confidence values to fuse; may be null or empty
     * @return maximum confidence value, or 0.0 if input is null/empty
     */
    public static double fuseMax(final double... confidences) {
        if (confidences == null || confidences.length == 0) {
            return EMPTY_RESULT;
        }
        
        double max = 0.0;
        for (final double c : confidences) {
            if (c > max) {
                max = c;
            }
        }
        return max;
    }

    /**
     * Fuses multiple confidence values using weighted average.
     * 
     * <p>This approach balances multiple signals according to their relative
     * importance. Signals with higher weights contribute more to the final result.
     * 
     * @param confidences array of confidence values
     * @param weights array of weights (must be same length as confidences)
     * @return weighted average confidence, or 0.0 if inputs are invalid
     */
    public static double fuseWeighted(final double[] confidences, final double[] weights) {
        if (confidences == null || weights == null || 
            confidences.length == 0 || confidences.length != weights.length) {
            return EMPTY_RESULT;
        }
        
        double weightedSum = 0.0;
        double weightSum = 0.0;
        
        for (int i = 0; i < confidences.length; i++) {
            weightedSum += confidences[i] * weights[i];
            weightSum += weights[i];
        }
        
        return weightSum > 0.0 ? weightedSum / weightSum : EMPTY_RESULT;
    }
}
