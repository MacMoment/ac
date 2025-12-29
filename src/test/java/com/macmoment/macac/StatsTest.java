package com.macmoment.macac;

import com.macmoment.macac.util.Stats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Stats utility class.
 */
class StatsTest {
    
    private static final double DELTA = 0.0001;
    
    // Median tests
    
    @Test
    void testMedianOddCount() {
        double[] values = {1.0, 3.0, 2.0, 5.0, 4.0};
        assertEquals(3.0, Stats.median(values), DELTA);
    }
    
    @Test
    void testMedianEvenCount() {
        double[] values = {1.0, 2.0, 3.0, 4.0};
        assertEquals(2.5, Stats.median(values), DELTA);
    }
    
    @Test
    void testMedianSingleValue() {
        double[] values = {42.0};
        assertEquals(42.0, Stats.median(values), DELTA);
    }
    
    @Test
    void testMedianEmpty() {
        assertEquals(0.0, Stats.median(new double[]{}), DELTA);
        assertEquals(0.0, Stats.median(null), DELTA);
    }
    
    @Test
    void testMedianSorted() {
        double[] values = {1.0, 2.0, 3.0, 4.0, 5.0};
        assertEquals(3.0, Stats.median(values), DELTA);
    }
    
    @Test
    void testMedianReverseSorted() {
        double[] values = {5.0, 4.0, 3.0, 2.0, 1.0};
        assertEquals(3.0, Stats.median(values), DELTA);
    }
    
    // MAD tests
    
    @Test
    void testMadBasic() {
        double[] values = {1.0, 2.0, 3.0, 4.0, 5.0};
        // Median = 3, deviations = [2, 1, 0, 1, 2], MAD = median of [0,1,1,2,2] = 1
        assertEquals(1.0, Stats.mad(values), DELTA);
    }
    
    @Test
    void testMadIdenticalValues() {
        double[] values = {5.0, 5.0, 5.0, 5.0};
        assertEquals(0.0, Stats.mad(values), DELTA);
    }
    
    @Test
    void testMadEmpty() {
        assertEquals(0.0, Stats.mad(new double[]{}), DELTA);
        assertEquals(0.0, Stats.mad(null), DELTA);
    }
    
    @Test
    void testMadSingleValue() {
        double[] values = {10.0};
        assertEquals(0.0, Stats.mad(values), DELTA);
    }
    
    // Mean tests
    
    @Test
    void testMeanBasic() {
        double[] values = {1.0, 2.0, 3.0, 4.0, 5.0};
        assertEquals(3.0, Stats.mean(values), DELTA);
    }
    
    @Test
    void testMeanSingleValue() {
        double[] values = {7.5};
        assertEquals(7.5, Stats.mean(values), DELTA);
    }
    
    @Test
    void testMeanEmpty() {
        assertEquals(0.0, Stats.mean(new double[]{}), DELTA);
        assertEquals(0.0, Stats.mean(null), DELTA);
    }
    
    // StdDev tests
    
    @Test
    void testStdDevBasic() {
        double[] values = {2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0};
        // Known stddev for this dataset (sample)
        double expected = 2.1380899;
        assertEquals(expected, Stats.stdDev(values), 0.001);
    }
    
    @Test
    void testStdDevIdentical() {
        double[] values = {5.0, 5.0, 5.0, 5.0};
        assertEquals(0.0, Stats.stdDev(values), DELTA);
    }
    
    @Test
    void testStdDevEmpty() {
        assertEquals(0.0, Stats.stdDev(new double[]{}), DELTA);
        assertEquals(0.0, Stats.stdDev(null), DELTA);
    }
    
    @Test
    void testStdDevSingleValue() {
        double[] values = {42.0};
        assertEquals(0.0, Stats.stdDev(values), DELTA);
    }
    
    // EWMA tests
    
    @Test
    void testEwmaBasic() {
        Stats.EWMA ewma = new Stats.EWMA(0.5);
        
        assertFalse(ewma.isInitialized());
        assertEquals(0.0, ewma.get(), DELTA);
        
        // First value initializes
        assertEquals(10.0, ewma.update(10.0), DELTA);
        assertTrue(ewma.isInitialized());
        assertEquals(10.0, ewma.get(), DELTA);
        
        // Second value: 0.5 * 20 + 0.5 * 10 = 15
        assertEquals(15.0, ewma.update(20.0), DELTA);
        
        // Third value: 0.5 * 10 + 0.5 * 15 = 12.5
        assertEquals(12.5, ewma.update(10.0), DELTA);
    }
    
    @Test
    void testEwmaReset() {
        Stats.EWMA ewma = new Stats.EWMA(0.3);
        ewma.update(100.0);
        ewma.update(200.0);
        
        ewma.reset();
        
        assertFalse(ewma.isInitialized());
        assertEquals(0.0, ewma.get(), DELTA);
        
        // Should reinitialize
        assertEquals(50.0, ewma.update(50.0), DELTA);
    }
    
    @Test
    void testEwmaInvalidAlpha() {
        assertThrows(IllegalArgumentException.class, () -> new Stats.EWMA(0.0));
        assertThrows(IllegalArgumentException.class, () -> new Stats.EWMA(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new Stats.EWMA(1.1));
    }
    
    @Test
    void testEwmaAlphaOne() {
        // Alpha = 1 means no smoothing (just takes latest value)
        Stats.EWMA ewma = new Stats.EWMA(1.0);
        ewma.update(10.0);
        assertEquals(10.0, ewma.get(), DELTA);
        ewma.update(99.0);
        assertEquals(99.0, ewma.get(), DELTA);
    }
    
    // RollingWindow tests
    
    @Test
    void testRollingWindowBasic() {
        Stats.RollingWindow window = new Stats.RollingWindow(5);
        
        assertTrue(window.isEmpty());
        assertEquals(0, window.size());
        
        window.add(1.0);
        window.add(2.0);
        window.add(3.0);
        
        assertEquals(3, window.size());
        assertEquals(2.0, window.median(), DELTA);
        assertEquals(2.0, window.mean(), DELTA);
    }
    
    @Test
    void testRollingWindowOverflow() {
        Stats.RollingWindow window = new Stats.RollingWindow(3);
        
        window.add(1.0);
        window.add(2.0);
        window.add(3.0);
        window.add(4.0);
        window.add(5.0);
        
        // Should contain 3, 4, 5
        assertEquals(3, window.size());
        assertEquals(4.0, window.median(), DELTA);
        assertEquals(4.0, window.mean(), DELTA);
    }
    
    @Test
    void testRollingWindowClear() {
        Stats.RollingWindow window = new Stats.RollingWindow(5);
        window.add(1.0);
        window.add(2.0);
        
        window.clear();
        
        assertTrue(window.isEmpty());
        assertEquals(0, window.size());
    }
    
    @Test
    void testRollingWindowInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Stats.RollingWindow(0));
        assertThrows(IllegalArgumentException.class, () -> new Stats.RollingWindow(-1));
    }
    
    // Confidence bounding tests
    
    @Test
    void testBoundConfidence() {
        assertEquals(0.0, Stats.boundConfidence(-1.0), DELTA);
        assertEquals(0.0, Stats.boundConfidence(0.0), DELTA);
        assertEquals(0.5, Stats.boundConfidence(0.5), DELTA);
        assertEquals(1.0, Stats.boundConfidence(1.0), DELTA);
        assertEquals(1.0, Stats.boundConfidence(1.5), DELTA);
        assertEquals(1.0, Stats.boundConfidence(100.0), DELTA);
    }
    
    @Test
    void testAnomalyToConfidence() {
        // Zero anomaly = zero confidence
        assertEquals(0.0, Stats.anomalyToConfidence(0.0, 1.0), DELTA);
        assertEquals(0.0, Stats.anomalyToConfidence(-1.0, 1.0), DELTA);
        
        // Positive anomaly = positive confidence < 1
        double conf = Stats.anomalyToConfidence(1.0, 1.0);
        assertTrue(conf > 0.0);
        assertTrue(conf < 1.0);
        
        // Higher anomaly = higher confidence
        double confHigh = Stats.anomalyToConfidence(5.0, 1.0);
        assertTrue(confHigh > conf);
        
        // Very high anomaly approaches 1.0
        double confVeryHigh = Stats.anomalyToConfidence(100.0, 1.0);
        assertTrue(confVeryHigh > 0.99);
        assertTrue(confVeryHigh <= 1.0);
    }
    
    @Test
    void testAnomalyToConfidenceScale() {
        // Higher scale = less sensitive (lower confidence for same anomaly)
        double conf1 = Stats.anomalyToConfidence(2.0, 1.0);
        double conf2 = Stats.anomalyToConfidence(2.0, 2.0);
        double conf3 = Stats.anomalyToConfidence(2.0, 5.0);
        
        assertTrue(conf1 > conf2);
        assertTrue(conf2 > conf3);
    }
    
    // Fusion tests
    
    @Test
    void testFuseMax() {
        assertEquals(0.0, Stats.fuseMax(), DELTA);
        assertEquals(0.0, Stats.fuseMax((double[]) null), DELTA);
        assertEquals(0.5, Stats.fuseMax(0.1, 0.2, 0.5, 0.3), DELTA);
        assertEquals(1.0, Stats.fuseMax(0.1, 1.0, 0.5), DELTA);
        assertEquals(0.7, Stats.fuseMax(0.7), DELTA);
    }
    
    @Test
    void testFuseWeighted() {
        double[] confidences = {0.5, 0.8, 0.3};
        double[] weights = {1.0, 2.0, 1.0};
        
        // Weighted avg: (0.5*1 + 0.8*2 + 0.3*1) / (1+2+1) = 2.4 / 4 = 0.6
        assertEquals(0.6, Stats.fuseWeighted(confidences, weights), DELTA);
    }
    
    @Test
    void testFuseWeightedEmpty() {
        assertEquals(0.0, Stats.fuseWeighted(null, null), DELTA);
        assertEquals(0.0, Stats.fuseWeighted(new double[]{}, new double[]{}), DELTA);
        assertEquals(0.0, Stats.fuseWeighted(new double[]{0.5}, new double[]{}), DELTA);
    }
    
    @Test
    void testFuseWeightedMismatchedLength() {
        double[] confidences = {0.5, 0.8};
        double[] weights = {1.0, 2.0, 3.0}; // Different length
        
        assertEquals(0.0, Stats.fuseWeighted(confidences, weights), DELTA);
    }
}
