package com.macmoment.macac.pipeline;

import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.pipeline.checks.MovementConsistencyCheck;
import com.macmoment.macac.pipeline.checks.PacketTimingCheck;
import com.macmoment.macac.pipeline.checks.PredictionDriftCheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registry for anti-cheat detection checks.
 * 
 * <p>This registry manages the lifecycle of all detection checks, including:
 * <ul>
 *   <li>Registration of built-in and custom checks</li>
 *   <li>Configuration propagation to all registered checks</li>
 *   <li>Retrieval of enabled checks for execution</li>
 * </ul>
 * 
 * <p>Built-in checks are registered automatically during construction:
 * <ul>
 *   <li>{@link PacketTimingCheck} - Detects packet timing anomalies</li>
 *   <li>{@link MovementConsistencyCheck} - Detects physics violations</li>
 *   <li>{@link PredictionDriftCheck} - Detects movement prediction drift</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This class is NOT thread-safe.
 * All access should be from a single thread or externally synchronized.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class CheckRegistry {
    
    private final List<Check> checks;
    
    /**
     * Creates a new check registry with built-in checks pre-registered.
     */
    public CheckRegistry() {
        this.checks = new ArrayList<>();
        
        // Register built-in movement checks
        checks.add(new PacketTimingCheck());
        checks.add(new MovementConsistencyCheck());
        checks.add(new PredictionDriftCheck());
    }
    
    /**
     * Configures all registered checks with the provided configuration.
     * 
     * <p>This should be called after loading or reloading configuration.
     * Each check receives the same configuration and extracts its own
     * relevant settings.
     * 
     * @param config engine configuration; must not be null
     * @throws NullPointerException if config is null
     */
    public void configure(final EngineConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        
        for (final Check check : checks) {
            check.configure(config);
        }
    }
    
    /**
     * Returns a list of all currently enabled checks.
     * 
     * <p>A check is enabled based on its configuration settings. Only enabled
     * checks should be executed during the detection pipeline.
     * 
     * @return unmodifiable list of enabled checks; never null, may be empty
     */
    public List<Check> getEnabledChecks() {
        final List<Check> enabled = new ArrayList<>();
        
        for (final Check check : checks) {
            if (check.isEnabled()) {
                enabled.add(check);
            }
        }
        
        return Collections.unmodifiableList(enabled);
    }
    
    /**
     * Returns all registered checks regardless of enabled status.
     * 
     * @return unmodifiable list of all registered checks; never null
     */
    public List<Check> getAllChecks() {
        return Collections.unmodifiableList(new ArrayList<>(checks));
    }
    
    /**
     * Retrieves a check by its unique name.
     * 
     * @param name check name to search for; must not be null
     * @return the matching check, or null if not found
     * @throws NullPointerException if name is null
     */
    public Check getByName(final String name) {
        Objects.requireNonNull(name, "name must not be null");
        
        for (final Check check : checks) {
            if (name.equals(check.getName())) {
                return check;
            }
        }
        return null;
    }
    
    /**
     * Registers a custom check.
     * 
     * <p>The check will be added only if no check with the same name is
     * already registered. This prevents duplicate registrations.
     * 
     * @param check the check to register; must not be null
     * @return true if the check was registered, false if a duplicate exists
     * @throws NullPointerException if check is null
     */
    public boolean register(final Check check) {
        Objects.requireNonNull(check, "check must not be null");
        
        if (getByName(check.getName()) != null) {
            return false;
        }
        
        checks.add(check);
        return true;
    }
    
    /**
     * Removes a check by name.
     * 
     * <p>This is primarily useful for testing or dynamic check management.
     * Removing built-in checks is permitted but not recommended.
     * 
     * @param name name of the check to remove; must not be null
     * @return true if a check was removed
     * @throws NullPointerException if name is null
     */
    public boolean unregister(final String name) {
        Objects.requireNonNull(name, "name must not be null");
        
        return checks.removeIf(check -> name.equals(check.getName()));
    }
    
    /**
     * Returns the total number of registered checks.
     * 
     * @return check count
     */
    public int size() {
        return checks.size();
    }
    
    /**
     * Returns the number of enabled checks.
     * 
     * @return enabled check count
     */
    public int enabledCount() {
        int count = 0;
        for (final Check check : checks) {
            if (check.isEnabled()) {
                count++;
            }
        }
        return count;
    }
}
