package com.macmoment.macac.util;

import java.util.Arrays;

/**
 * Statistical utility methods for anti-cheat analysis.
 * Provides median, MAD (Median Absolute Deviation), and EWMA calculations.
 */
public final class Stats {

    private Stats() {
        // Utility class
    }

    /**
     * Calculates the median of an array of values.
     * 
     * @param values Array of values (will be sorted in place)
     * @return Median value, or 0 if empty
     */
    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
        return sorted[mid];
    }

    /**
     * Calculates the Median Absolute Deviation (MAD).
     * MAD is a robust measure of variability.
     * 
     * @param values Array of values
     * @return MAD value, or 0 if empty
     */
    public static double mad(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double med = median(values);
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - med);
        }
        return median(deviations);
    }

    /**
     * Calculates the mean of an array of values.
     * 
     * @param values Array of values
     * @return Mean value, or 0 if empty
     */
    public static double mean(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /**
     * Calculates the standard deviation of an array of values.
     * 
     * @param values Array of values
     * @return Standard deviation, or 0 if empty
     */
    public static double stdDev(double[] values) {
        if (values == null || values.length < 2) {
            return 0.0;
        }
        double mean = mean(values);
        double sumSquares = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSquares += diff * diff;
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    /**
     * EWMA (Exponentially Weighted Moving Average) calculator.
     * Maintains state across updates for streaming calculation.
     */
    public static class EWMA {
        private final double alpha;
        private double value;
        private boolean initialized;

        /**
         * Creates a new EWMA calculator.
         * 
         * @param alpha Smoothing factor (0.0-1.0). Higher = more reactive to new values.
         */
        public EWMA(double alpha) {
            if (alpha <= 0.0 || alpha > 1.0) {
                throw new IllegalArgumentException("Alpha must be in range (0.0, 1.0]");
            }
            this.alpha = alpha;
            this.value = 0.0;
            this.initialized = false;
        }

        /**
         * Updates the EWMA with a new value.
         * 
         * @param newValue New observation
         * @return Updated EWMA value
         */
        public double update(double newValue) {
            if (!initialized) {
                value = newValue;
                initialized = true;
            } else {
                value = alpha * newValue + (1 - alpha) * value;
            }
            return value;
        }

        /**
         * Returns the current EWMA value.
         * 
         * @return Current EWMA, or 0 if not initialized
         */
        public double get() {
            return value;
        }

        /**
         * Returns true if at least one value has been added.
         * 
         * @return true if initialized
         */
        public boolean isInitialized() {
            return initialized;
        }

        /**
         * Resets the EWMA to uninitialized state.
         */
        public void reset() {
            value = 0.0;
            initialized = false;
        }
    }

    /**
     * Rolling window for maintaining a fixed-size collection of values.
     * Efficient for computing statistics over recent observations.
     */
    public static class RollingWindow {
        private final double[] values;
        private int head;
        private int size;

        /**
         * Creates a new rolling window.
         * 
         * @param capacity Maximum number of values to retain
         */
        public RollingWindow(int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("Capacity must be at least 1");
            }
            this.values = new double[capacity];
            this.head = 0;
            this.size = 0;
        }

        /**
         * Adds a value to the window.
         * 
         * @param value Value to add
         */
        public void add(double value) {
            values[head] = value;
            head = (head + 1) % values.length;
            if (size < values.length) {
                size++;
            }
        }

        /**
         * Returns all values in the window as an array.
         * 
         * @return Array of values
         */
        public double[] toArray() {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                int index = (head - size + i + values.length) % values.length;
                result[i] = values[index];
            }
            return result;
        }

        /**
         * Returns the current size of the window.
         * 
         * @return Number of values
         */
        public int size() {
            return size;
        }

        /**
         * Returns true if the window is empty.
         * 
         * @return true if empty
         */
        public boolean isEmpty() {
            return size == 0;
        }

        /**
         * Clears the window.
         */
        public void clear() {
            head = 0;
            size = 0;
        }

        /**
         * Returns the median of values in the window.
         * 
         * @return Median value
         */
        public double median() {
            return Stats.median(toArray());
        }

        /**
         * Returns the MAD of values in the window.
         * 
         * @return MAD value
         */
        public double mad() {
            return Stats.mad(toArray());
        }

        /**
         * Returns the mean of values in the window.
         * 
         * @return Mean value
         */
        public double mean() {
            return Stats.mean(toArray());
        }

        /**
         * Returns the standard deviation of values in the window.
         * 
         * @return Standard deviation
         */
        public double stdDev() {
            return Stats.stdDev(toArray());
        }
        
        /**
         * Returns the minimum value in the window.
         * 
         * @return Minimum value, or 0 if empty
         */
        public double min() {
            if (size == 0) return 0.0;
            double[] arr = toArray();
            double min = Double.MAX_VALUE;
            for (double v : arr) {
                if (v < min) min = v;
            }
            return min;
        }
        
        /**
         * Returns the maximum value in the window.
         * 
         * @return Maximum value, or 0 if empty
         */
        public double max() {
            if (size == 0) return 0.0;
            double[] arr = toArray();
            double max = Double.MIN_VALUE;
            for (double v : arr) {
                if (v > max) max = v;
            }
            return max;
        }
    }

    /**
     * Bounds a value to the range [0.0, 1.0].
     * 
     * @param value Value to bound
     * @return Bounded value
     */
    public static double boundConfidence(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Converts an anomaly score to a confidence value using a sigmoid function.
     * Higher anomaly scores produce higher confidence values (closer to 1.0).
     * 
     * @param anomalyScore Raw anomaly score (unbounded)
     * @param scale Scaling factor for sigmoid sensitivity
     * @return Confidence value in [0.0, 1.0]
     */
    public static double anomalyToConfidence(double anomalyScore, double scale) {
        if (anomalyScore <= 0) {
            return 0.0;
        }
        // Sigmoid transformation: 2 / (1 + e^(-x/scale)) - 1
        // This maps [0, inf) to [0, 1)
        double exp = Math.exp(-anomalyScore / scale);
        return boundConfidence(2.0 / (1.0 + exp) - 1.0);
    }

    /**
     * Fuses multiple confidence values using maximum (conservative approach).
     * 
     * @param confidences Array of confidence values
     * @return Maximum confidence value
     */
    public static double fuseMax(double... confidences) {
        if (confidences == null || confidences.length == 0) {
            return 0.0;
        }
        double max = 0.0;
        for (double c : confidences) {
            if (c > max) {
                max = c;
            }
        }
        return max;
    }

    /**
     * Fuses multiple confidence values using weighted average.
     * 
     * @param confidences Array of confidence values
     * @param weights Array of weights (must be same length)
     * @return Weighted average confidence
     */
    public static double fuseWeighted(double[] confidences, double[] weights) {
        if (confidences == null || weights == null || 
            confidences.length == 0 || confidences.length != weights.length) {
            return 0.0;
        }
        double sum = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < confidences.length; i++) {
            sum += confidences[i] * weights[i];
            weightSum += weights[i];
        }
        return weightSum > 0 ? sum / weightSum : 0.0;
    }
}
