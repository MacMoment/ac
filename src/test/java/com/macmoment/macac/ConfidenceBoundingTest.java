package com.macmoment.macac;

import com.macmoment.macac.util.Stats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for confidence bounding behavior across the system.
 */
class ConfidenceBoundingTest {
    
    private static final double DELTA = 0.0001;
    
    @Test
    void testBoundConfidenceNegative() {
        assertEquals(0.0, Stats.boundConfidence(-0.001), DELTA);
        assertEquals(0.0, Stats.boundConfidence(-1.0), DELTA);
        assertEquals(0.0, Stats.boundConfidence(Double.NEGATIVE_INFINITY), DELTA);
    }
    
    @Test
    void testBoundConfidenceZero() {
        assertEquals(0.0, Stats.boundConfidence(0.0), DELTA);
    }
    
    @Test
    void testBoundConfidenceInRange() {
        assertEquals(0.001, Stats.boundConfidence(0.001), DELTA);
        assertEquals(0.5, Stats.boundConfidence(0.5), DELTA);
        assertEquals(0.997, Stats.boundConfidence(0.997), DELTA);
        assertEquals(0.999, Stats.boundConfidence(0.999), DELTA);
        assertEquals(1.0, Stats.boundConfidence(1.0), DELTA);
    }
    
    @Test
    void testBoundConfidenceAboveOne() {
        assertEquals(1.0, Stats.boundConfidence(1.001), DELTA);
        assertEquals(1.0, Stats.boundConfidence(2.0), DELTA);
        assertEquals(1.0, Stats.boundConfidence(100.0), DELTA);
        // Note: Double.POSITIVE_INFINITY should also return 1.0
        assertEquals(1.0, Stats.boundConfidence(Double.POSITIVE_INFINITY), DELTA);
    }
    
    @Test
    void testAnomalyToConfidenceMonotonicity() {
        // Test that anomalyToConfidence is monotonically increasing
        double scale = 1.0;
        double prev = Stats.anomalyToConfidence(0.0, scale);
        
        for (double anomaly = 0.1; anomaly <= 10.0; anomaly += 0.1) {
            double current = Stats.anomalyToConfidence(anomaly, scale);
            assertTrue(current >= prev, 
                "Confidence should be monotonically increasing. " +
                "prev=" + prev + " current=" + current + " anomaly=" + anomaly);
            prev = current;
        }
    }
    
    @Test
    void testAnomalyToConfidenceBounds() {
        // Result should always be in [0, 1]
        for (double anomaly = -10.0; anomaly <= 100.0; anomaly += 0.5) {
            for (double scale = 0.1; scale <= 5.0; scale += 0.5) {
                double result = Stats.anomalyToConfidence(anomaly, scale);
                assertTrue(result >= 0.0, 
                    "Confidence must be >= 0. Got " + result + " for anomaly=" + anomaly);
                assertTrue(result <= 1.0, 
                    "Confidence must be <= 1. Got " + result + " for anomaly=" + anomaly);
            }
        }
    }
    
    @Test
    void testAnomalyToConfidenceZeroAnomaly() {
        assertEquals(0.0, Stats.anomalyToConfidence(0.0, 1.0), DELTA);
        assertEquals(0.0, Stats.anomalyToConfidence(0.0, 0.5), DELTA);
        assertEquals(0.0, Stats.anomalyToConfidence(0.0, 2.0), DELTA);
    }
    
    @Test
    void testAnomalyToConfidenceHighAnomaly() {
        // Very high anomaly should approach but never exceed 1.0
        double result = Stats.anomalyToConfidence(1000.0, 1.0);
        assertTrue(result > 0.99, "High anomaly should give confidence > 0.99");
        assertTrue(result <= 1.0, "Confidence should never exceed 1.0");
    }
    
    @Test
    void testFuseMaxPreservesRange() {
        // Test that fusing confidences preserves the [0,1] range
        double[] confidences = {0.1, 0.5, 0.8, 0.3};
        double result = Stats.fuseMax(confidences);
        
        assertTrue(result >= 0.0 && result <= 1.0);
        assertEquals(0.8, result, DELTA);
    }
    
    @Test
    void testFuseWeightedPreservesRange() {
        double[] confidences = {0.1, 0.5, 0.9};
        double[] weights = {1.0, 1.0, 1.0};
        
        double result = Stats.fuseWeighted(confidences, weights);
        assertTrue(result >= 0.0 && result <= 1.0);
    }
    
    @Test
    void testEwmaConfidenceTracking() {
        // EWMA should track confidence values while staying bounded
        Stats.EWMA ewma = new Stats.EWMA(0.3);
        
        // Start with low confidence
        ewma.update(0.1);
        assertTrue(ewma.get() >= 0.0 && ewma.get() <= 1.0);
        
        // Add high confidence values
        ewma.update(0.9);
        ewma.update(0.8);
        ewma.update(0.85);
        
        // Should still be bounded
        double result = ewma.get();
        assertTrue(result >= 0.0 && result <= 1.0, 
            "EWMA result should be bounded: " + result);
    }
    
    @Test
    void testConfidencePrecisionAtThreshold() {
        // Test precision around the default action threshold (0.997)
        double threshold = 0.997;
        
        // Values just below threshold
        double below = 0.996999;
        assertFalse(Stats.boundConfidence(below) >= threshold, 
            "Value below threshold should not trigger");
        
        // Values at threshold
        double at = 0.997;
        assertTrue(Stats.boundConfidence(at) >= threshold, 
            "Value at threshold should trigger");
        
        // Values just above threshold
        double above = 0.997001;
        assertTrue(Stats.boundConfidence(above) >= threshold, 
            "Value above threshold should trigger");
    }
    
    @Test
    void testConfidenceAggregationWithZeros() {
        // Ensure aggregation handles zeros properly
        double result = Stats.fuseMax(0.0, 0.0, 0.0);
        assertEquals(0.0, result, DELTA);
        
        result = Stats.fuseMax(0.0, 0.5, 0.0);
        assertEquals(0.5, result, DELTA);
    }
    
    @Test
    void testRollingWindowStatsBounded() {
        Stats.RollingWindow window = new Stats.RollingWindow(10);
        
        // Add confidence values
        for (int i = 0; i < 20; i++) {
            window.add(0.5 + (i % 5) * 0.1); // Values 0.5 to 0.9
        }
        
        double median = window.median();
        double mean = window.mean();
        
        // Both should be within reasonable confidence range
        assertTrue(median >= 0.0 && median <= 1.0, 
            "Median should be bounded: " + median);
        assertTrue(mean >= 0.0 && mean <= 1.0, 
            "Mean should be bounded: " + mean);
    }
}
