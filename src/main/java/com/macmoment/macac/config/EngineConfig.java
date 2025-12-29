package com.macmoment.macac.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration holder for MacAC engine.
 * Loads and validates all settings from config.yml.
 */
public final class EngineConfig {
    // Thresholds
    private double actionConfidence;
    private double minSeverity;
    private double punishmentThreshold;
    
    // Windows (converted to nanos for internal use)
    private long exemptionNanos;
    private long cooldownNanos;
    private long lagGraceNanos;
    
    // History
    private int historySize;
    private int medianWindowSize;
    
    // Checks
    private boolean packetTimingEnabled;
    private double packetTimingWeight;
    private long packetTimingMinDeltaMs;
    private double packetTimingMaxJitter;
    
    private boolean movementConsistencyEnabled;
    private double movementConsistencyWeight;
    private double maxHorizSpeed;
    private double maxVertSpeed;
    private double accelTolerance;
    
    private boolean predictionDriftEnabled;
    private double predictionDriftWeight;
    private int minDriftSamples;
    private double maxDriftThreshold;
    
    // Actions
    private boolean alertsEnabled;
    private boolean consoleLogEnabled;
    private String alertFormat;
    private boolean punishmentEnabled;
    private String punishmentType;
    private long punishmentDelayMs;
    
    // Exemptions
    private Set<UUID> whitelist;
    private String bypassPermission;
    private boolean exemptCreative;
    private boolean exemptSpectator;
    
    // Performance
    private int maxChecksPerTick;
    private boolean asyncPackets;
    private boolean debug;
    
    // Stats
    private boolean useEwma;
    private double ewmaAlpha;

    /**
     * Loads configuration from the plugin's config.yml.
     * 
     * @param plugin The plugin instance
     * @return Loaded configuration
     */
    public static EngineConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        
        EngineConfig ec = new EngineConfig();
        
        // Thresholds
        ec.actionConfidence = clamp(config.getDouble("thresholds.action_confidence", 0.997), 0.0, 1.0);
        ec.minSeverity = clamp(config.getDouble("thresholds.min_severity", 0.3), 0.0, 1.0);
        ec.punishmentThreshold = clamp(config.getDouble("actions.punishment.threshold", 0.999), 0.0, 1.0);
        
        // Windows (convert ms to nanos)
        ec.exemptionNanos = config.getLong("windows.exemption_ms", 250) * 1_000_000L;
        ec.cooldownNanos = config.getLong("windows.cooldown_ms", 1500) * 1_000_000L;
        ec.lagGraceNanos = config.getLong("windows.lag_grace_ms", 500) * 1_000_000L;
        
        // History
        ec.historySize = Math.max(1, config.getInt("history.size", 64));
        ec.medianWindowSize = Math.max(1, config.getInt("stats.median_window", 20));
        
        // Packet timing check
        ec.packetTimingEnabled = config.getBoolean("checks.packet_timing.enabled", true);
        ec.packetTimingWeight = clamp(config.getDouble("checks.packet_timing.weight", 1.0), 0.0, 10.0);
        ec.packetTimingMinDeltaMs = config.getLong("checks.packet_timing.min_delta_ms", 5);
        ec.packetTimingMaxJitter = config.getDouble("checks.packet_timing.max_jitter_coefficient", 3.0);
        
        // Movement consistency check
        ec.movementConsistencyEnabled = config.getBoolean("checks.movement_consistency.enabled", true);
        ec.movementConsistencyWeight = clamp(config.getDouble("checks.movement_consistency.weight", 1.0), 0.0, 10.0);
        ec.maxHorizSpeed = config.getDouble("checks.movement_consistency.max_horiz_speed", 0.8);
        ec.maxVertSpeed = config.getDouble("checks.movement_consistency.max_vert_speed", 0.6);
        ec.accelTolerance = config.getDouble("checks.movement_consistency.accel_tolerance", 1.5);
        
        // Prediction drift check
        ec.predictionDriftEnabled = config.getBoolean("checks.prediction_drift.enabled", true);
        ec.predictionDriftWeight = clamp(config.getDouble("checks.prediction_drift.weight", 1.0), 0.0, 10.0);
        ec.minDriftSamples = Math.max(1, config.getInt("checks.prediction_drift.min_drift_samples", 5));
        ec.maxDriftThreshold = config.getDouble("checks.prediction_drift.max_drift_threshold", 0.5);
        
        // Actions
        ec.alertsEnabled = config.getBoolean("actions.alerts.enabled", true);
        ec.consoleLogEnabled = config.getBoolean("actions.alerts.console_log", true);
        ec.alertFormat = config.getString("actions.alerts.format", 
            "&c[MacAC] &e{player} &7flagged for &f{category} &7(conf: &c{confidence}&7, sev: &e{severity}&7)");
        ec.punishmentEnabled = config.getBoolean("actions.punishment.enabled", false);
        ec.punishmentType = config.getString("actions.punishment.type", "FLAG_ONLY");
        ec.punishmentDelayMs = config.getLong("actions.punishment.delay_ms", 0);
        
        // Exemptions
        ec.whitelist = new HashSet<>();
        List<String> uuids = config.getStringList("exemptions.whitelist");
        for (String uuidStr : uuids) {
            try {
                ec.whitelist.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUIDs
            }
        }
        ec.bypassPermission = config.getString("exemptions.bypass_permission", "macac.bypass");
        ec.exemptCreative = config.getBoolean("exemptions.exempt_creative", true);
        ec.exemptSpectator = config.getBoolean("exemptions.exempt_spectator", true);
        
        // Performance
        ec.maxChecksPerTick = Math.max(1, config.getInt("performance.max_checks_per_tick", 10));
        ec.asyncPackets = config.getBoolean("performance.async_packets", true);
        ec.debug = config.getBoolean("performance.debug", false);
        
        // Stats
        ec.useEwma = config.getBoolean("stats.use_ewma", true);
        ec.ewmaAlpha = clamp(config.getDouble("stats.ewma_alpha", 0.3), 0.01, 1.0);
        
        return ec;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Getters
    public double getActionConfidence() { return actionConfidence; }
    public double getMinSeverity() { return minSeverity; }
    public double getPunishmentThreshold() { return punishmentThreshold; }
    
    public long getExemptionNanos() { return exemptionNanos; }
    public long getCooldownNanos() { return cooldownNanos; }
    public long getLagGraceNanos() { return lagGraceNanos; }
    
    public int getHistorySize() { return historySize; }
    public int getMedianWindowSize() { return medianWindowSize; }
    
    public boolean isPacketTimingEnabled() { return packetTimingEnabled; }
    public double getPacketTimingWeight() { return packetTimingWeight; }
    public long getPacketTimingMinDeltaMs() { return packetTimingMinDeltaMs; }
    public double getPacketTimingMaxJitter() { return packetTimingMaxJitter; }
    
    public boolean isMovementConsistencyEnabled() { return movementConsistencyEnabled; }
    public double getMovementConsistencyWeight() { return movementConsistencyWeight; }
    public double getMaxHorizSpeed() { return maxHorizSpeed; }
    public double getMaxVertSpeed() { return maxVertSpeed; }
    public double getAccelTolerance() { return accelTolerance; }
    
    public boolean isPredictionDriftEnabled() { return predictionDriftEnabled; }
    public double getPredictionDriftWeight() { return predictionDriftWeight; }
    public int getMinDriftSamples() { return minDriftSamples; }
    public double getMaxDriftThreshold() { return maxDriftThreshold; }
    
    public boolean isAlertsEnabled() { return alertsEnabled; }
    public boolean isConsoleLogEnabled() { return consoleLogEnabled; }
    public String getAlertFormat() { return alertFormat; }
    public boolean isPunishmentEnabled() { return punishmentEnabled; }
    public String getPunishmentType() { return punishmentType; }
    public long getPunishmentDelayMs() { return punishmentDelayMs; }
    
    public Set<UUID> getWhitelist() { return whitelist; }
    public String getBypassPermission() { return bypassPermission; }
    public boolean isExemptCreative() { return exemptCreative; }
    public boolean isExemptSpectator() { return exemptSpectator; }
    
    public int getMaxChecksPerTick() { return maxChecksPerTick; }
    public boolean isAsyncPackets() { return asyncPackets; }
    public boolean isDebug() { return debug; }
    
    public boolean isUseEwma() { return useEwma; }
    public double getEwmaAlpha() { return ewmaAlpha; }
}
