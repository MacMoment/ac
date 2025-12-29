package com.macmoment.macac.model;

import java.util.Map;

/**
 * Result of a combat check analysis.
 * Shared by all combat checks to avoid code duplication.
 */
public record CombatCheckResult(
    String checkName,
    double confidence,
    double severity,
    Map<String, Object> explanation,
    boolean isViolation
) {
    /**
     * Creates a clean result (no violation detected).
     * 
     * @param name Check name
     * @return Clean result
     */
    public static CombatCheckResult clean(String name) {
        return new CombatCheckResult(name, 0.0, 0.0, Map.of(), false);
    }
    
    /**
     * Creates a violation result.
     * 
     * @param name Check name
     * @param confidence Confidence score (0.0-1.0)
     * @param severity Severity score (0.0-1.0)
     * @param explanation Explanation map with detection details
     * @return Violation result
     */
    public static CombatCheckResult violation(String name, double confidence, 
                                              double severity, Map<String, Object> explanation) {
        return new CombatCheckResult(name, confidence, severity, explanation, true);
    }
}
