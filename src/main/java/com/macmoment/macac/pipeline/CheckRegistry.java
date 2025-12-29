package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.pipeline.checks.MovementConsistencyCheck;
import com.macmoment.macac.pipeline.checks.PacketTimingCheck;
import com.macmoment.macac.pipeline.checks.PredictionDriftCheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry for all anti-cheat checks.
 * Manages check lifecycle and configuration.
 */
public final class CheckRegistry {
    
    private final List<Check> checks;
    
    public CheckRegistry() {
        this.checks = new ArrayList<>();
        
        // Register built-in checks
        checks.add(new PacketTimingCheck());
        checks.add(new MovementConsistencyCheck());
        checks.add(new PredictionDriftCheck());
    }
    
    /**
     * Configures all registered checks.
     * 
     * @param config Engine configuration
     */
    public void configure(EngineConfig config) {
        for (Check check : checks) {
            check.configure(config);
        }
    }
    
    /**
     * Returns all enabled checks.
     * 
     * @return List of enabled checks
     */
    public List<Check> getEnabledChecks() {
        List<Check> enabled = new ArrayList<>();
        for (Check check : checks) {
            if (check.isEnabled()) {
                enabled.add(check);
            }
        }
        return Collections.unmodifiableList(enabled);
    }
    
    /**
     * Returns all registered checks.
     * 
     * @return List of all checks
     */
    public List<Check> getAllChecks() {
        return Collections.unmodifiableList(checks);
    }
    
    /**
     * Gets a check by name.
     * 
     * @param name Check name
     * @return Check or null
     */
    public Check getByName(String name) {
        for (Check check : checks) {
            if (check.getName().equals(name)) {
                return check;
            }
        }
        return null;
    }
    
    /**
     * Registers a custom check.
     * 
     * @param check Check to register
     */
    public void register(Check check) {
        if (check != null && getByName(check.getName()) == null) {
            checks.add(check);
        }
    }
    
    /**
     * Returns the number of registered checks.
     * 
     * @return Check count
     */
    public int size() {
        return checks.size();
    }
}
