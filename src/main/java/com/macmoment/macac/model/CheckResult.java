package com.macmoment.macac.model;

import java.util.Map;
import java.util.Objects;

/**
 * Result from a single check execution.
 * 
 * <p>Each check produces a CheckResult indicating whether suspicious behavior
 * was detected, with an associated confidence level, severity, and explanation.
 * 
 * <p>This is an immutable record:
 * <ul>
 *   <li>{@code checkName} - Unique identifier for the check</li>
 *   <li>{@code confidence} - How certain we are this is a violation (0.0-1.0)</li>
 *   <li>{@code severity} - How severe the violation is if real (0.0-1.0)</li>
 *   <li>{@code explain} - Key-value pairs explaining what triggered the detection</li>
 * </ul>
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public record CheckResult(
    String checkName,           // Name of the check that produced this result
    double confidence,          // Confidence that this is a violation (0.0-1.0)
    double severity,            // Severity of the violation (0.0-1.0)
    Map<String, Object> explain // Key-value pairs explaining the detection
) {
    
    /** Minimum valid confidence/severity value. */
    private static final double MIN_VALUE = 0.0;
    
    /** Maximum valid confidence/severity value. */
    private static final double MAX_VALUE = 1.0;
    
    /**
     * Compact constructor that validates inputs.
     */
    public CheckResult {
        Objects.requireNonNull(checkName, "checkName must not be null");
        Objects.requireNonNull(explain, "explain must not be null");
        
        // Clamp confidence and severity to valid range
        confidence = clamp(confidence);
        severity = clamp(severity);
    }
    
    /**
     * Creates a "clean" result indicating no violation detected.
     * 
     * <p>Use this when a check finds no suspicious activity.
     * 
     * @param checkName name of the check; must not be null
     * @return CheckResult with zero confidence and severity
     */
    public static CheckResult clean(final String checkName) {
        Objects.requireNonNull(checkName, "checkName must not be null");
        return new CheckResult(checkName, 0.0, 0.0, Map.of());
    }

    /**
     * Creates a violation result.
     * 
     * <p>Confidence and severity values are clamped to [0.0, 1.0].
     * 
     * @param checkName name of the check; must not be null
     * @param confidence confidence level (0.0-1.0)
     * @param severity severity level (0.0-1.0)
     * @param explain explanation map; must not be null
     * @return CheckResult indicating a potential violation
     */
    public static CheckResult violation(final String checkName, final double confidence, 
                                        final double severity, final Map<String, Object> explain) {
        return new CheckResult(checkName, confidence, severity, explain);
    }

    /**
     * Returns true if this result indicates a potential violation.
     * 
     * @param threshold confidence threshold to exceed
     * @return true if confidence is at or above threshold
     */
    public boolean isViolation(final double threshold) {
        return confidence >= threshold;
    }
    
    /**
     * Returns true if this is a clean result (zero confidence).
     * 
     * @return true if confidence is zero
     */
    public boolean isClean() {
        return confidence <= MIN_VALUE;
    }
    
    /**
     * Clamps a value to the valid range [0.0, 1.0].
     */
    private static double clamp(final double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }
}
