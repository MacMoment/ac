package com.macmoment.macac.core;

import com.macmoment.macac.actions.AlertPublisher;
import com.macmoment.macac.actions.PunishmentHandler;
import com.macmoment.macac.actions.WhitelistManager;
import com.macmoment.macac.config.EngineConfig;
import com.macmoment.macac.ingest.PacketIngestor;
import com.macmoment.macac.ingest.impl.FallbackEventIngestor;
import com.macmoment.macac.ingest.impl.ProtocolLibPacketIngestor;
import com.macmoment.macac.model.*;
import com.macmoment.macac.pipeline.*;
import com.macmoment.macac.util.MonoClock;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Core anti-cheat engine that orchestrates all components.
 * Manages the pipeline from packet ingestion to action execution.
 */
public final class Engine {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final MonoClock clock;
    
    // Configuration
    private EngineConfig config;
    
    // Components
    private PacketIngestor ingestor;
    private final FeatureExtractor featureExtractor;
    private final HistoryStore historyStore;
    private final CheckRegistry checkRegistry;
    private final Aggregator aggregator;
    private final MitigationPolicy mitigationPolicy;
    private final AlertPublisher alertPublisher;
    private final PunishmentHandler punishmentHandler;
    private final WhitelistManager whitelistManager;
    
    // State
    private volatile boolean running;
    
    public Engine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.clock = MonoClock.SYSTEM;
        
        // Initialize components
        this.featureExtractor = new FeatureExtractor();
        this.historyStore = new HistoryStore();
        this.checkRegistry = new CheckRegistry();
        this.aggregator = new Aggregator();
        this.mitigationPolicy = new MitigationPolicy(clock);
        this.alertPublisher = new AlertPublisher(plugin);
        this.punishmentHandler = new PunishmentHandler(plugin);
        this.whitelistManager = new WhitelistManager();
        
        this.running = false;
    }
    
    /**
     * Loads configuration and initializes the engine.
     */
    public void initialize() {
        logger.info("Initializing MacAC engine...");
        
        // Load configuration
        config = EngineConfig.load(plugin);
        
        // Configure components
        historyStore.configure(config);
        checkRegistry.configure(config);
        aggregator.configure(config);
        mitigationPolicy.configure(config);
        alertPublisher.configure(config);
        punishmentHandler.configure(config);
        whitelistManager.configure(config);
        
        // Initialize ingestor
        initializeIngestor();
        
        logger.info("MacAC engine initialized with " + checkRegistry.getEnabledChecks().size() + " checks enabled");
    }
    
    /**
     * Starts the anti-cheat engine.
     */
    public void start() {
        if (running) {
            return;
        }
        
        // Start ingestor
        if (ingestor != null) {
            ingestor.setCallback(this::processTelemetry);
            ingestor.start();
        }
        
        running = true;
        logger.info("MacAC engine started using " + (ingestor != null ? ingestor.getName() : "no") + " ingestor");
    }
    
    /**
     * Stops the anti-cheat engine.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        if (ingestor != null) {
            ingestor.stop();
        }
        
        historyStore.clear();
        logger.info("MacAC engine stopped");
    }
    
    /**
     * Reloads configuration.
     */
    public void reload() {
        logger.info("Reloading MacAC configuration...");
        
        config = EngineConfig.load(plugin);
        
        historyStore.configure(config);
        checkRegistry.configure(config);
        aggregator.configure(config);
        mitigationPolicy.configure(config);
        alertPublisher.configure(config);
        punishmentHandler.configure(config);
        whitelistManager.configure(config);
        
        logger.info("MacAC configuration reloaded");
    }
    
    /**
     * Initializes the packet ingestor based on available dependencies.
     */
    private void initializeIngestor() {
        // Try ProtocolLib first
        if (ProtocolLibPacketIngestor.isProtocolLibAvailable()) {
            logger.info("ProtocolLib detected, using packet-level interception");
            ingestor = new ProtocolLibPacketIngestor(plugin, clock);
        } else {
            logger.info("ProtocolLib not found, using fallback event-based detection");
            ingestor = new FallbackEventIngestor(plugin, clock);
        }
    }
    
    /**
     * Main processing pipeline for incoming telemetry.
     * This is called for each movement packet/event.
     */
    private void processTelemetry(Player player, TelemetryInput input) {
        if (!running || player == null || input == null) {
            return;
        }
        
        try {
            UUID playerId = player.getUniqueId();
            String playerName = player.getName();
            
            // Check whitelist early
            if (whitelistManager.isExempt(playerId)) {
                return;
            }
            
            // Get or create player context
            PlayerContext context = historyStore.getOrCreate(playerId, playerName);
            
            // Add telemetry to history
            context.addTelemetry(input);
            
            // Extract features
            Features features = featureExtractor.extract(input, context);
            context.addFeatures(features);
            
            // Check for lag-based exemption
            if (features.isLagging()) {
                mitigationPolicy.markLagExempt(context);
                debugLog("Player " + playerName + " marked as lagging");
                return;
            }
            
            // Run enabled checks
            List<CheckResult> results = new ArrayList<>();
            for (Check check : checkRegistry.getEnabledChecks()) {
                try {
                    CheckResult result = check.analyze(input, features, context);
                    results.add(result);
                } catch (Exception e) {
                    logger.warning("Error in check " + check.getName() + ": " + e.getMessage());
                }
            }
            
            // Aggregate results
            Violation violation = aggregator.aggregate(results, context, 
                input.nanoTime(), input.ping());
            
            if (violation == null) {
                return;
            }
            
            // Apply mitigation policy
            Decision decision = mitigationPolicy.evaluate(violation, context, player);
            
            // Execute decision
            executeDecision(decision);
            
        } catch (Exception e) {
            logger.severe("Error processing telemetry: " + e.getMessage());
            if (config.isDebug()) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Executes a decision.
     */
    private void executeDecision(Decision decision) {
        if (decision == null || !decision.requiresAction()) {
            return;
        }
        
        switch (decision.action()) {
            case ALERT:
                alertPublisher.publish(decision.violation());
                break;
            case PUNISH:
                alertPublisher.publish(decision.violation());
                punishmentHandler.execute(decision);
                break;
            case FLAG:
                // Just log for FLAG_ONLY
                debugLog("FLAG: " + decision.violation().playerName() + 
                        " - " + decision.violation().category());
                break;
            case NONE:
            default:
                break;
        }
    }
    
    /**
     * Called when a player joins.
     */
    public void onPlayerJoin(UUID playerId, String playerName) {
        PlayerContext context = historyStore.getOrCreate(playerId, playerName);
        mitigationPolicy.setRecentJoin(context, true);
        
        // Clear recent join flag after exemption window
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerContext ctx = historyStore.get(playerId);
            if (ctx != null) {
                mitigationPolicy.setRecentJoin(ctx, false);
            }
        }, 20L); // 1 second (20 ticks)
    }
    
    /**
     * Called when a player quits.
     */
    public void onPlayerQuit(UUID playerId) {
        historyStore.remove(playerId);
        
        // Clean up ingestor state
        if (ingestor instanceof FallbackEventIngestor fallback) {
            fallback.removePlayer(playerId);
        } else if (ingestor instanceof ProtocolLibPacketIngestor protocolLib) {
            protocolLib.removePlayer(playerId);
        }
    }
    
    /**
     * Called when a player teleports.
     */
    public void onPlayerTeleport(UUID playerId) {
        PlayerContext context = historyStore.get(playerId);
        if (context != null) {
            mitigationPolicy.setTeleporting(context, true);
            
            // Clear teleporting flag after exemption
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PlayerContext ctx = historyStore.get(playerId);
                if (ctx != null) {
                    mitigationPolicy.setTeleporting(ctx, false);
                }
            }, 10L); // 0.5 seconds
        }
    }
    
    /**
     * Called when a player changes worlds.
     */
    public void onWorldChange(UUID playerId) {
        PlayerContext context = historyStore.get(playerId);
        if (context != null) {
            mitigationPolicy.setWorldChanging(context, true);
            context.reset(); // Clear history for world change
            
            // Clear flag after exemption
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PlayerContext ctx = historyStore.get(playerId);
                if (ctx != null) {
                    mitigationPolicy.setWorldChanging(ctx, false);
                }
            }, 20L);
        }
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     */
    private void debugLog(String message) {
        if (config != null && config.isDebug()) {
            logger.info("[DEBUG] " + message);
        }
    }
    
    // Getters for components
    public EngineConfig getConfig() { return config; }
    public HistoryStore getHistoryStore() { return historyStore; }
    public CheckRegistry getCheckRegistry() { return checkRegistry; }
    public WhitelistManager getWhitelistManager() { return whitelistManager; }
    public boolean isRunning() { return running; }
}
