package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CheckResult;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.Violation;
import com.macmoment.macac.util.Stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates results from multiple detection checks into a single violation.
 * 
 * <p>The aggregator combines confidence scores from individual checks using
 * a configurable fusion strategy. The current implementation uses a
 * max-confidence (conservative) approach, where the highest individual
 * confidence becomes the aggregate confidence.
 * 
 * <p>Aggregation thresholds control when a violation is generated:
 * <ul>
 *   <li><strong>Action threshold</strong>: Minimum aggregated confidence required</li>
 *   <li><strong>Minimum severity</strong>: Minimum severity level required</li>
 * </ul>
 * 
 * <p>Both thresholds must be exceeded for a violation to be generated.
 * This dual-threshold approach helps reduce false positives by ensuring
 * both confidence and severity are sufficiently high.
 * 
 * <p><strong>Thread Safety:</strong> This class is NOT thread-safe.
 * Access should be from a single thread or externally synchronized.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class Aggregator {
    
    /** Default action threshold for high confidence requirement. */
    private static final double DEFAULT_ACTION_THRESHOLD = 0.997;
    
    /** Default minimum severity level. */
    private static final double DEFAULT_MIN_SEVERITY = 0.3;
    
    /** Minimum confidence to consider a result significant. */
    private static final double SIGNIFICANCE_THRESHOLD = 0.1;
    
    private double actionThreshold;
    private double minSeverity;
    
    /**
     * Creates a new aggregator with default thresholds.
     */
    public Aggregator() {
        this.actionThreshold = DEFAULT_ACTION_THRESHOLD;
        this.minSeverity = DEFAULT_MIN_SEVERITY;
    }
    
    /**
     * Updates aggregator configuration from engine settings.
     * 
     * @param config engine configuration; must not be null
     * @throws NullPointerException if config is null
     */
    public void configure(final EngineConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        
        this.actionThreshold = config.getActionConfidence();
        this.minSeverity = config.getMinSeverity();
    }
    
    /**
     * Aggregates check results into a violation if thresholds are exceeded.
     * 
     * <p>The aggregation process:
     * <ol>
     *   <li>Filter out results below significance threshold</li>
     *   <li>Compute max confidence and max severity from significant results</li>
     *   <li>Determine primary category from highest-confidence check</li>
     *   <li>Return null if thresholds not met, otherwise create violation</li>
     * </ol>
     * 
     * @param results results from all executed checks; may be null or empty
     * @param context player context for the aggregated violation
     * @param timestamp monotonic timestamp of detection
     * @param ping player ping at detection time
     * @return violation if thresholds exceeded, null otherwise
     */
    public Violation aggregate(final List<CheckResult> results, final PlayerContext context, 
                               final long timestamp, final long ping) {
        // Early exit for empty results
        if (results == null || results.isEmpty()) {
            return null;
        }
        
        // Filter to significant results only
        final List<CheckResult> significantResults = filterSignificant(results);
        
        if (significantResults.isEmpty()) {
            return null;
        }
        
        // Find maximum confidence and severity
        double maxConfidence = 0.0;
        double maxSeverity = 0.0;
        String primaryCategory = "unknown";
        
        for (final CheckResult result : significantResults) {
            final double confidence = result.confidence();
            final double severity = result.severity();
            
            if (confidence > maxConfidence) {
                maxConfidence = confidence;
                primaryCategory = result.checkName();
            }
            
            if (severity > maxSeverity) {
                maxSeverity = severity;
            }
        }
        
        // Check against thresholds
        if (maxConfidence < actionThreshold || maxSeverity < minSeverity) {
            return null;
        }
        
        // Build and return the violation
        return Violation.fromResults(
            context.getPlayerId(),
            context.getPlayerName(),
            primaryCategory,
            significantResults,
            timestamp,
            ping
        );
    }
    
    /**
     * Filters results to only include those above the significance threshold.
     */
    private List<CheckResult> filterSignificant(final List<CheckResult> results) {
        final List<CheckResult> significant = new ArrayList<>();
        
        for (final CheckResult result : results) {
            if (result.confidence() > SIGNIFICANCE_THRESHOLD) {
                significant.add(result);
            }
        }
        
        return significant;
    }
    
    /**
     * Calculates weighted confidence from results using provided weights.
     * 
     * <p>This is an alternative fusion method to max-confidence that can
     * be used for fine-tuning detection sensitivity.
     * 
     * @param results check results; must have same length as weights
     * @param weights corresponding weights for each result
     * @return weighted confidence value, or 0.0 if inputs are invalid
     */
    public double calculateWeightedConfidence(final List<CheckResult> results, 
                                              final List<Double> weights) {
        if (results == null || weights == null || 
            results.size() != weights.size() || results.isEmpty()) {
            return 0.0;
        }
        
        final double[] confidences = new double[results.size()];
        final double[] weightArr = new double[weights.size()];
        
        for (int i = 0; i < results.size(); i++) {
            confidences[i] = results.get(i).confidence();
            weightArr[i] = weights.get(i);
        }
        
        return Stats.fuseWeighted(confidences, weightArr);
    }
    
    /**
     * Returns the current action threshold.
     * 
     * <p>Aggregated confidence must exceed this threshold for a violation
     * to be generated.
     * 
     * @return action threshold (0.0-1.0)
     */
    public double getActionThreshold() {
        return actionThreshold;
    }
    
    /**
     * Returns the current minimum severity threshold.
     * 
     * @return minimum severity (0.0-1.0)
     */
    public double getMinSeverity() {
        return minSeverity;
    }
}
