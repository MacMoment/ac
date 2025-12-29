package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CheckResult;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.Violation;
import com.macmoment.macac.util.Stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates results from multiple checks into a single confidence score.
 * Uses conservative fusion (max-confidence) with optional weighting.
 */
public final class Aggregator {
    
    private double actionThreshold;
    private double minSeverity;
    
    public Aggregator() {
        this.actionThreshold = 0.997;
        this.minSeverity = 0.3;
    }
    
    /**
     * Updates configuration.
     * 
     * @param config Engine configuration
     */
    public void configure(EngineConfig config) {
        this.actionThreshold = config.getActionConfidence();
        this.minSeverity = config.getMinSeverity();
    }
    
    /**
     * Aggregates check results into a violation if thresholds are exceeded.
     * 
     * @param results Results from all checks
     * @param context Player context
     * @param timestamp Current timestamp
     * @param ping Current ping
     * @return Violation if thresholds exceeded, null otherwise
     */
    public Violation aggregate(List<CheckResult> results, PlayerContext context, 
                               long timestamp, long ping) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        
        // Filter out clean results
        List<CheckResult> significantResults = new ArrayList<>();
        for (CheckResult result : results) {
            if (result.confidence() > 0.1) {
                significantResults.add(result);
            }
        }
        
        if (significantResults.isEmpty()) {
            return null;
        }
        
        // Calculate aggregated confidence using max (conservative approach)
        double maxConfidence = 0.0;
        double maxSeverity = 0.0;
        String primaryCategory = "unknown";
        
        for (CheckResult result : significantResults) {
            if (result.confidence() > maxConfidence) {
                maxConfidence = result.confidence();
                primaryCategory = results.stream()
                    .filter(r -> r.checkName().equals(result.checkName()))
                    .findFirst()
                    .map(r -> r.checkName())
                    .orElse("unknown");
            }
            if (result.severity() > maxSeverity) {
                maxSeverity = result.severity();
            }
        }
        
        // Check thresholds
        if (maxConfidence < actionThreshold || maxSeverity < minSeverity) {
            return null;
        }
        
        // Build violation
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
     * Calculates weighted confidence from results.
     * Alternative to max-confidence for tuning.
     * 
     * @param results Check results
     * @param weights Corresponding weights
     * @return Weighted confidence
     */
    public double calculateWeightedConfidence(List<CheckResult> results, List<Double> weights) {
        if (results.size() != weights.size()) {
            return 0.0;
        }
        
        double[] confidences = new double[results.size()];
        double[] weightArr = new double[weights.size()];
        
        for (int i = 0; i < results.size(); i++) {
            confidences[i] = results.get(i).confidence();
            weightArr[i] = weights.get(i);
        }
        
        return Stats.fuseWeighted(confidences, weightArr);
    }
    
    /**
     * Returns the action threshold.
     * 
     * @return Action threshold
     */
    public double getActionThreshold() {
        return actionThreshold;
    }
}
