package org.bukkit.configuration.file;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub FileConfiguration class for standalone compilation.
 * In production, this is provided by the Paper API.
 */
public class FileConfiguration {
    private final Map<String, Object> values = new HashMap<>();

    public FileConfiguration() {
        // Set defaults
        setDefaults();
    }

    private void setDefaults() {
        values.put("thresholds.action_confidence", 0.997);
        values.put("thresholds.min_severity", 0.3);
        values.put("windows.exemption_ms", 250L);
        values.put("windows.cooldown_ms", 1500L);
        values.put("windows.lag_grace_ms", 500L);
        values.put("history.size", 64);
        values.put("stats.median_window", 20);
        values.put("stats.use_ewma", true);
        values.put("stats.ewma_alpha", 0.3);
        values.put("checks.packet_timing.enabled", true);
        values.put("checks.packet_timing.weight", 1.0);
        values.put("checks.packet_timing.min_delta_ms", 5L);
        values.put("checks.packet_timing.max_jitter_coefficient", 3.0);
        values.put("checks.movement_consistency.enabled", true);
        values.put("checks.movement_consistency.weight", 1.0);
        values.put("checks.movement_consistency.max_horiz_speed", 0.8);
        values.put("checks.movement_consistency.max_vert_speed", 0.6);
        values.put("checks.movement_consistency.accel_tolerance", 1.5);
        values.put("checks.prediction_drift.enabled", true);
        values.put("checks.prediction_drift.weight", 1.0);
        values.put("checks.prediction_drift.min_drift_samples", 5);
        values.put("checks.prediction_drift.max_drift_threshold", 0.5);
        
        // Combat checks - Aimbot
        values.put("checks.combat_aimbot.enabled", true);
        values.put("checks.combat_aimbot.weight", 1.0);
        values.put("checks.combat_aimbot.max_snap_angle", 45.0);
        values.put("checks.combat_aimbot.min_aim_variance", 1.0);
        values.put("checks.combat_aimbot.max_aim_perfection", 3.0);
        
        // Combat checks - Reach
        values.put("checks.combat_reach.enabled", true);
        values.put("checks.combat_reach.weight", 1.0);
        values.put("checks.combat_reach.max_reach", 3.5);
        values.put("checks.combat_reach.reach_buffer", 0.3);
        
        // Combat checks - Auto-clicker/Killaura
        values.put("checks.combat_autoclicker.enabled", true);
        values.put("checks.combat_autoclicker.weight", 1.0);
        values.put("checks.combat_autoclicker.max_hit_rate", 0.85);
        values.put("checks.combat_autoclicker.min_attack_interval", 40.0);
        values.put("checks.combat_autoclicker.max_interval_consistency", 0.05);
        
        // Combat shared settings
        values.put("checks.combat.min_samples", 10);
        values.put("checks.combat.history_size", 50);
        
        values.put("actions.alerts.enabled", true);
        values.put("actions.alerts.console_log", true);
        values.put("actions.alerts.format", "&c[MacAC] &e{player} &7flagged for &f{category}");
        values.put("actions.punishment.enabled", false);
        values.put("actions.punishment.type", "FLAG_ONLY");
        values.put("actions.punishment.threshold", 0.999);
        values.put("actions.punishment.delay_ms", 0L);
        values.put("exemptions.bypass_permission", "macac.bypass");
        values.put("exemptions.exempt_creative", true);
        values.put("exemptions.exempt_spectator", true);
        values.put("performance.max_checks_per_tick", 10);
        values.put("performance.async_packets", true);
        values.put("performance.debug", false);
    }

    public String getString(String path, String def) {
        Object value = values.get(path);
        return value instanceof String ? (String) value : def;
    }

    public int getInt(String path, int def) {
        Object value = values.get(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return def;
    }

    public long getLong(String path, long def) {
        Object value = values.get(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return def;
    }

    public double getDouble(String path, double def) {
        Object value = values.get(path);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = values.get(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = values.get(path);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    public void set(String path, Object value) {
        values.put(path, value);
    }
}
