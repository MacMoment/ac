package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.model.CheckResult;
import com.macmoment.macac.model.Features;
import com.macmoment.macac.model.PlayerContext;
import com.macmoment.macac.model.TelemetryInput;

/**
 * Base interface for all anti-cheat detection checks.
 * 
 * <p>A check analyzes player telemetry and extracted features to produce a
 * confidence-based result indicating whether suspicious behavior was detected.
 * Checks are the core detection units in the anti-cheat pipeline.
 * 
 * <p><strong>Implementation Guidelines:</strong>
 * <ul>
 *   <li>Checks should be stateless or maintain only per-player state via PlayerContext</li>
 *   <li>Return {@link CheckResult#clean(String)} for no detection, not null</li>
 *   <li>Confidence should reflect actual certainty, not just anomaly magnitude</li>
 *   <li>Consider ping/lag compensation in all threshold calculations</li>
 *   <li>Include relevant diagnostic data in the explanation map</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> Check implementations should be thread-safe
 * if they will be called from multiple threads. The player context handles its
 * own synchronization.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 * @see CheckResult
 * @see CheckRegistry
 */
public interface Check {
    
    /**
     * Returns the unique name of this check.
     * 
     * <p>The name is used for:
     * <ul>
     *   <li>Configuration key lookups</li>
     *   <li>Alert and logging messages</li>
     *   <li>Registry lookup via {@link CheckRegistry#getByName(String)}</li>
     * </ul>
     * 
     * @return unique check name; never null or empty
     */
    String getName();
    
    /**
     * Returns the category of this check.
     * 
     * <p>Categories group related checks and are used for:
     * <ul>
     *   <li>Alert categorization</li>
     *   <li>Per-category configuration</li>
     *   <li>Statistical reporting</li>
     * </ul>
     * 
     * <p>Common categories: "movement", "timing", "combat", "world"
     * 
     * @return check category; never null or empty
     */
    String getCategory();
    
    /**
     * Returns whether this check is currently enabled.
     * 
     * <p>Disabled checks are not executed in the detection pipeline.
     * Enable/disable state is typically controlled by configuration.
     * 
     * @return true if this check should be executed
     */
    boolean isEnabled();
    
    /**
     * Returns the weight for confidence aggregation.
     * 
     * <p>Higher weights give this check more influence when using weighted
     * fusion in the aggregator. A weight of 0 effectively disables the check
     * for aggregation purposes.
     * 
     * @return weight value; typically 0.0-2.0, default 1.0
     */
    double getWeight();
    
    /**
     * Analyzes current telemetry and context to produce a detection result.
     * 
     * <p>This is the core detection method. It receives:
     * <ul>
     *   <li>Current raw telemetry input (movement, rotation, flags)</li>
     *   <li>Extracted features computed from telemetry</li>
     *   <li>Player context with historical data and state</li>
     * </ul>
     * 
     * <p>The implementation should:
     * <ol>
     *   <li>Check early-exit conditions (disabled, insufficient history, exemptions)</li>
     *   <li>Perform detection analysis using input, features, and history</li>
     *   <li>Return a CheckResult with appropriate confidence and explanation</li>
     * </ol>
     * 
     * @param input current telemetry input from the player
     * @param features extracted features computed from telemetry
     * @param context player context containing history and state
     * @return check result with confidence, severity, and explanation; never null
     * @throws RuntimeException if an unrecoverable error occurs during analysis
     */
    CheckResult analyze(TelemetryInput input, Features features, PlayerContext context);
    
    /**
     * Resets any internal state for a player.
     * 
     * <p>Called when a player disconnects, changes worlds, or when state
     * should be cleared for another reason. Default implementation is a no-op.
     * 
     * <p>Most checks should not need to override this as per-player state
     * is typically stored in the PlayerContext.
     * 
     * @param context player context to reset state for
     */
    default void reset(final PlayerContext context) {
        // Default: no-op - most checks don't maintain separate state
    }
    
    /**
     * Updates check configuration.
     * 
     * <p>Called during engine initialization and on configuration reload.
     * Implementations should extract their relevant settings from the config.
     * 
     * @param config new engine configuration; never null
     */
    void configure(EngineConfig config);
}
