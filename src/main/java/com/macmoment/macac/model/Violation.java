package com.macmoment.macac.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregated violation data from multiple checks.
 * Used for alert generation and action decisions.
 */
public record Violation(
    UUID playerId,
    String playerName,
    String category,                    // Primary violation category
    double confidence,                  // Aggregated confidence
    double severity,                    // Maximum severity
    long timestamp,                     // When violation was detected
    long ping,                          // Player ping at detection
    List<CheckResult> checkResults,     // Individual check results
    Map<String, Object> explanation     // Combined explanation
) {
    /**
     * Creates a violation from aggregated check results.
     * 
     * @param playerId Player UUID
     * @param playerName Player name
     * @param category Primary category
     * @param checkResults Results from checks
     * @param timestamp Detection timestamp
     * @param ping Player ping
     * @return Violation instance
     */
    public static Violation fromResults(UUID playerId, String playerName, String category,
                                        List<CheckResult> checkResults, long timestamp, long ping) {
        double maxConfidence = checkResults.stream()
            .mapToDouble(CheckResult::confidence)
            .max()
            .orElse(0.0);
        
        double maxSeverity = checkResults.stream()
            .mapToDouble(CheckResult::severity)
            .max()
            .orElse(0.0);
        
        // Combine explanations from all checks
        Map<String, Object> combinedExplain = checkResults.stream()
            .filter(r -> r.confidence() > 0)
            .flatMap(r -> r.explain().entrySet().stream())
            .collect(Collectors.toMap(
                e -> e.getKey(),
                e -> e.getValue(),
                (v1, v2) -> v1 // Keep first on collision
            ));

        return new Violation(
            playerId, playerName, category,
            maxConfidence, maxSeverity,
            timestamp, ping,
            checkResults, combinedExplain
        );
    }

    /**
     * Generates a human-readable explanation string.
     * 
     * @return Formatted explanation
     */
    public String getFormattedExplanation() {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(category);
        sb.append(", Confidence: ").append(String.format("%.4f", confidence));
        sb.append(", Severity: ").append(String.format("%.2f", severity));
        sb.append(", Ping: ").append(ping).append("ms");
        
        if (!explanation.isEmpty()) {
            sb.append(" | Signals: ");
            explanation.forEach((k, v) -> {
                sb.append(k).append("=");
                if (v instanceof Double d) {
                    sb.append(String.format("%.3f", d));
                } else {
                    sb.append(v);
                }
                sb.append(", ");
            });
            // Remove trailing comma
            sb.setLength(sb.length() - 2);
        }
        
        return sb.toString();
    }

    /**
     * Returns the names of checks that triggered.
     * 
     * @param threshold Confidence threshold
     * @return List of check names
     */
    public List<String> getTriggeredCheckNames(double threshold) {
        return checkResults.stream()
            .filter(r -> r.confidence() >= threshold)
            .map(CheckResult::checkName)
            .toList();
    }
}
