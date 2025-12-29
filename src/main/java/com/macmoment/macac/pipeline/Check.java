package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CheckResult;
import com.macmoment.macac.model.Features;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.TelemetryInput;

/**
 * Base interface for all anti-cheat checks.
 * Checks analyze telemetry and features to produce confidence-based results.
 */
public interface Check {
    
    /**
     * Returns the unique name of this check.
     * 
     * @return Check name
     */
    String getName();
    
    /**
     * Returns the category of this check (e.g., "movement", "timing", "combat").
     * 
     * @return Check category
     */
    String getCategory();
    
    /**
     * Returns true if this check is currently enabled.
     * 
     * @return true if enabled
     */
    boolean isEnabled();
    
    /**
     * Returns the weight for confidence aggregation.
     * 
     * @return Weight value
     */
    double getWeight();
    
    /**
     * Analyzes the current input and context to produce a result.
     * 
     * @param input Current telemetry input
     * @param features Extracted features
     * @param context Player context with history
     * @return Check result with confidence and explanation
     */
    CheckResult analyze(TelemetryInput input, Features features, PlayerContext context);
    
    /**
     * Resets any internal state for a player.
     * Called on player disconnect or world change.
     * 
     * @param context Player context
     */
    default void reset(PlayerContext context) {
        // Default: no-op
    }
    
    /**
     * Updates configuration.
     * Called on config reload.
     * 
     * @param config New configuration
     */
    void configure(EngineConfig config);
}
