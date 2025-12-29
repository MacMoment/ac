package com.macmoment.macac.model;

import java.util.Map;

/**
 * Result from a single check execution.
 * Contains confidence, severity, and explanation data.
 */
public record CheckResult(
    String checkName,           // Name of the check that produced this result
    double confidence,          // Confidence that this is a violation (0.0-1.0)
    double severity,            // Severity of the violation (0.0-1.0)
    Map<String, Object> explain // Key-value pairs explaining the detection
) {
    /**
     * Creates a "clean" result indicating no violation detected.
     * 
     * @param checkName Name of the check
     * @return CheckResult with zero confidence
     */
    public static CheckResult clean(String checkName) {
        return new CheckResult(checkName, 0.0, 0.0, Map.of());
    }

    /**
     * Creates a violation result.
     * 
     * @param checkName Name of the check
     * @param confidence Confidence level (0.0-1.0)
     * @param severity Severity level (0.0-1.0)
     * @param explain Explanation map
     * @return CheckResult
     */
    public static CheckResult violation(String checkName, double confidence, 
                                        double severity, Map<String, Object> explain) {
        return new CheckResult(
            checkName,
            Math.max(0.0, Math.min(1.0, confidence)),
            Math.max(0.0, Math.min(1.0, severity)),
            explain
        );
    }

    /**
     * Returns true if this result indicates a potential violation.
     * 
     * @param threshold Confidence threshold
     * @return true if confidence exceeds threshold
     */
    public boolean isViolation(double threshold) {
        return confidence >= threshold;
    }
}
