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
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core anti-cheat engine that orchestrates all detection components.
 * 
 * <p>The engine manages the complete detection pipeline:
 * <ol>
 *   <li>Packet/event ingestion via {@link PacketIngestor}</li>
 *   <li>Feature extraction from raw telemetry</li>
 *   <li>Check execution and result aggregation</li>
 *   <li>Mitigation policy evaluation</li>
 *   <li>Alert publication and punishment execution</li>
 * </ol>
 * 
 * <p>Lifecycle: The engine must be {@link #initialize() initialized} before
 * {@link #start() starting}, and should be {@link #stop() stopped} when no
 * longer needed to release resources.
 * 
 * <p><strong>Thread Safety:</strong> This class is designed for single-threaded
 * access from the main server thread, with the exception of telemetry processing
 * which may occur asynchronously depending on the ingestor configuration.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class Engine {
    
    /** Ticks for join exemption window (1 second = 20 ticks). */
    private static final long JOIN_EXEMPTION_TICKS = 20L;
    
    /** Ticks for teleport exemption window (0.5 seconds = 10 ticks). */
    private static final long TELEPORT_EXEMPTION_TICKS = 10L;
    
    /** Ticks for world change exemption window (1 second = 20 ticks). */
    private static final long WORLD_CHANGE_EXEMPTION_TICKS = 20L;
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final MonoClock clock;
    
    // Configuration (reloaded on config reload)
    private volatile EngineConfig config;
    
    // Core pipeline components
    private PacketIngestor ingestor;
    private final FeatureExtractor featureExtractor;
    private final HistoryStore historyStore;
    private final CheckRegistry checkRegistry;
    private final Aggregator aggregator;
    private final MitigationPolicy mitigationPolicy;
    private final AlertPublisher alertPublisher;
    private final PunishmentHandler punishmentHandler;
    private final WhitelistManager whitelistManager;
    
    // Engine state
    private volatile boolean running;
    
    /**
     * Creates a new engine instance for the specified plugin.
     * 
     * <p>After construction, call {@link #initialize()} to load configuration
     * and prepare components, then {@link #start()} to begin detection.
     * 
     * @param plugin the owning plugin; must not be null
     * @throws NullPointerException if plugin is null
     */
    public Engine(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.logger = plugin.getLogger();
        this.clock = MonoClock.SYSTEM;
        
        // Initialize all pipeline components
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
     * Initializes the engine by loading configuration and preparing components.
     * 
     * <p>This method must be called before {@link #start()}. It loads
     * configuration from the plugin's config.yml, configures all components,
     * and initializes the appropriate packet ingestor.
     */
    public void initialize() {
        logger.info("Initializing MacAC engine...");
        
        // Load configuration from plugin
        config = EngineConfig.load(plugin);
        
        // Configure all pipeline components
        configureComponents();
        
        // Initialize the packet/event ingestor
        initializeIngestor();
        
        final int enabledChecks = checkRegistry.getEnabledChecks().size();
        logger.info("MacAC engine initialized with " + enabledChecks + " checks enabled");
    }
    
    /**
     * Starts the anti-cheat detection engine.
     * 
     * <p>After this call, the engine will begin processing player movement
     * data and generating alerts/punishments as configured.
     * 
     * <p>This method is idempotent; calling it when already running has no effect.
     */
    public void start() {
        if (running) {
            logger.fine("Engine already running, ignoring start request");
            return;
        }
        
        // Start the ingestor with our telemetry callback
        if (ingestor != null) {
            ingestor.setCallback(this::processTelemetry);
            ingestor.start();
        }
        
        running = true;
        
        final String ingestorName = (ingestor != null) ? ingestor.getName() : "none";
        logger.info("MacAC engine started using " + ingestorName + " ingestor");
    }
    
    /**
     * Stops the anti-cheat detection engine.
     * 
     * <p>After this call, no further detection will occur. All player state
     * is cleared. This method is idempotent.
     */
    public void stop() {
        if (!running) {
            logger.fine("Engine already stopped, ignoring stop request");
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
     * Reloads configuration from disk and reconfigures all components.
     * 
     * <p>This method can be called while the engine is running. New
     * configuration takes effect immediately for subsequent detections.
     */
    public void reload() {
        logger.info("Reloading MacAC configuration...");
        
        config = EngineConfig.load(plugin);
        configureComponents();
        
        logger.info("MacAC configuration reloaded");
    }
    
    /**
     * Configures all pipeline components with current configuration.
     */
    private void configureComponents() {
        historyStore.configure(config);
        checkRegistry.configure(config);
        aggregator.configure(config);
        mitigationPolicy.configure(config);
        alertPublisher.configure(config);
        punishmentHandler.configure(config);
        whitelistManager.configure(config);
    }
    
    /**
     * Initializes the packet ingestor based on available server plugins.
     * 
     * <p>Prefers ProtocolLib for lower-latency packet-level detection,
     * falling back to Bukkit events if ProtocolLib is not available.
     */
    private void initializeIngestor() {
        if (ProtocolLibPacketIngestor.isProtocolLibAvailable()) {
            logger.info("ProtocolLib detected, using packet-level interception");
            ingestor = new ProtocolLibPacketIngestor(plugin, clock);
        } else {
            logger.info("ProtocolLib not found, using fallback event-based detection");
            ingestor = new FallbackEventIngestor(plugin, clock);
        }
    }
    
    /**
     * Main telemetry processing pipeline.
     * 
     * <p>This method is called for each movement packet/event and executes
     * the complete detection pipeline: feature extraction, check execution,
     * result aggregation, and action execution.
     * 
     * @param player the player whose movement is being processed
     * @param input the telemetry data from the movement
     */
    private void processTelemetry(final Player player, final TelemetryInput input) {
        // Early exit conditions
        if (!running || player == null || input == null) {
            return;
        }
        
        try {
            final UUID playerId = player.getUniqueId();
            final String playerName = player.getName();
            
            // Check exemptions early to avoid unnecessary work
            if (whitelistManager.isExempt(playerId)) {
                return;
            }
            
            // Get or create player context
            final PlayerContext context = historyStore.getOrCreate(playerId, playerName);
            
            // Add telemetry to history
            context.addTelemetry(input);
            
            // Extract features from raw telemetry
            final Features features = featureExtractor.extract(input, context);
            context.addFeatures(features);
            
            // Handle lag-based exemption
            if (features.isLagging()) {
                mitigationPolicy.markLagExempt(context);
                debugLog("Player " + playerName + " marked as lagging");
                return;
            }
            
            // Execute all enabled checks
            final List<CheckResult> results = executeChecks(input, features, context);
            
            // Aggregate check results into a potential violation
            final Violation violation = aggregator.aggregate(
                results, context, input.nanoTime(), input.ping());
            
            if (violation == null) {
                return;
            }
            
            // Apply mitigation policy to determine action
            final Decision decision = mitigationPolicy.evaluate(violation, context, player);
            
            // Execute the decision
            executeDecision(decision);
            
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Error processing telemetry", e);
            if (config != null && config.isDebug()) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Executes all enabled checks and collects results.
     */
    private List<CheckResult> executeChecks(final TelemetryInput input, 
                                            final Features features, 
                                            final PlayerContext context) {
        final List<Check> enabledChecks = checkRegistry.getEnabledChecks();
        final List<CheckResult> results = new ArrayList<>(enabledChecks.size());
        
        for (final Check check : enabledChecks) {
            try {
                final CheckResult result = check.analyze(input, features, context);
                results.add(result);
            } catch (final Exception e) {
                logger.warning("Error in check " + check.getName() + ": " + e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Executes the action specified by a decision.
     */
    private void executeDecision(final Decision decision) {
        if (decision == null || !decision.requiresAction()) {
            return;
        }
        
        switch (decision.action()) {
            case ALERT -> alertPublisher.publish(decision.violation());
            case PUNISH -> {
                alertPublisher.publish(decision.violation());
                punishmentHandler.execute(decision);
            }
            case FLAG -> debugLog("FLAG: " + decision.violation().playerName() + 
                                  " - " + decision.violation().category());
            case NONE -> { /* No action required */ }
        }
    }
    
    // ========================================================================
    // Player Lifecycle Methods
    // ========================================================================
    
    /**
     * Called when a player joins the server.
     * 
     * <p>Initializes player context and sets a temporary exemption window
     * to allow for connection stabilization.
     * 
     * @param playerId player's unique identifier
     * @param playerName player's display name
     */
    public void onPlayerJoin(final UUID playerId, final String playerName) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(playerName, "playerName must not be null");
        
        final PlayerContext context = historyStore.getOrCreate(playerId, playerName);
        mitigationPolicy.setRecentJoin(context, true);
        
        // Clear recent join flag after exemption window
        scheduleTask(() -> {
            final PlayerContext ctx = historyStore.get(playerId);
            if (ctx != null) {
                mitigationPolicy.setRecentJoin(ctx, false);
            }
        }, JOIN_EXEMPTION_TICKS);
    }
    
    /**
     * Called when a player leaves the server.
     * 
     * <p>Cleans up all player state to release resources.
     * 
     * @param playerId player's unique identifier
     */
    public void onPlayerQuit(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        
        historyStore.remove(playerId);
        
        // Clean up ingestor-specific state
        if (ingestor instanceof FallbackEventIngestor fallback) {
            fallback.removePlayer(playerId);
        } else if (ingestor instanceof ProtocolLibPacketIngestor protocolLib) {
            protocolLib.removePlayer(playerId);
        }
    }
    
    /**
     * Called when a player teleports.
     * 
     * <p>Sets a temporary exemption window to prevent false positives from
     * the large position change.
     * 
     * @param playerId player's unique identifier
     */
    public void onPlayerTeleport(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        
        final PlayerContext context = historyStore.get(playerId);
        if (context == null) {
            return;
        }
        
        mitigationPolicy.setTeleporting(context, true);
        
        // Clear teleporting flag after exemption window
        scheduleTask(() -> {
            final PlayerContext ctx = historyStore.get(playerId);
            if (ctx != null) {
                mitigationPolicy.setTeleporting(ctx, false);
            }
        }, TELEPORT_EXEMPTION_TICKS);
    }
    
    /**
     * Called when a player changes worlds.
     * 
     * <p>Resets player state and sets a temporary exemption window for
     * world transition.
     * 
     * @param playerId player's unique identifier
     */
    public void onWorldChange(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        
        final PlayerContext context = historyStore.get(playerId);
        if (context == null) {
            return;
        }
        
        mitigationPolicy.setWorldChanging(context, true);
        context.reset(); // Clear history for new world
        
        // Clear flag after exemption window
        scheduleTask(() -> {
            final PlayerContext ctx = historyStore.get(playerId);
            if (ctx != null) {
                mitigationPolicy.setWorldChanging(ctx, false);
            }
        }, WORLD_CHANGE_EXEMPTION_TICKS);
    }
    
    // ========================================================================
    // Utility Methods
    // ========================================================================
    
    /**
     * Schedules a task to run after the specified delay.
     */
    private void scheduleTask(final Runnable task, final long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     */
    private void debugLog(final String message) {
        if (config != null && config.isDebug()) {
            logger.info("[DEBUG] " + message);
        }
    }
    
    // ========================================================================
    // Component Accessors
    // ========================================================================
    
    /**
     * Returns the current engine configuration.
     * 
     * @return configuration; may be null before initialization
     */
    public EngineConfig getConfig() { 
        return config; 
    }
    
    /**
     * Returns the player history store.
     * 
     * @return history store; never null after construction
     */
    public HistoryStore getHistoryStore() { 
        return historyStore; 
    }
    
    /**
     * Returns the check registry.
     * 
     * @return check registry; never null after construction
     */
    public CheckRegistry getCheckRegistry() { 
        return checkRegistry; 
    }
    
    /**
     * Returns the whitelist manager.
     * 
     * @return whitelist manager; never null after construction
     */
    public WhitelistManager getWhitelistManager() { 
        return whitelistManager; 
    }
    
    /**
     * Returns whether the engine is currently running.
     * 
     * @return true if running and processing telemetry
     */
    public boolean isRunning() { 
        return running; 
    }
}
