package com.macmoment.macac.model;

import com.macmoment.macac.util.RingBuffer;
import com.macmoment.macac.util.Stats;

import java.util.UUID;

/**
 * Per-player combat context containing combat state, history, and statistics.
 * This class is thread-safe for single-writer operations.
 */
public final class CombatContext {
    private final UUID playerId;
    private final String playerName;
    
    // Combat history buffers
    private final RingBuffer<CombatInput> combatHistory;
    private final Stats.RollingWindow aimErrorWindow;
    private final Stats.RollingWindow snapAngleWindow;
    private final Stats.RollingWindow reachWindow;
    private final Stats.RollingWindow attackIntervalWindow;
    private final Stats.RollingWindow hitRateWindow;
    
    // EWMA trackers for combat metrics
    private final Stats.EWMA aimErrorEwma;
    private final Stats.EWMA snapAngleEwma;
    private final Stats.EWMA reachEwma;
    private final Stats.EWMA attackIntervalEwma;
    
    // Combat statistics
    private volatile int totalAttacks;
    private volatile int totalHits;
    private volatile int totalCriticals;
    private volatile long lastAttackNanos;
    private volatile long lastAlertNanos;
    private volatile long exemptUntilNanos;
    
    // Recent window statistics
    private volatile int recentHits;
    private volatile int recentAttacks;
    
    // Target tracking for multi-target detection
    private volatile UUID lastTargetId;
    private volatile int consecutiveTargetHits;
    
    /**
     * Creates a new combat context.
     * 
     * @param playerId Player UUID
     * @param playerName Player name
     * @param historySize Size of combat history buffer
     * @param windowSize Size of rolling windows
     * @param ewmaAlpha EWMA smoothing factor
     */
    public CombatContext(UUID playerId, String playerName, int historySize, 
                         int windowSize, double ewmaAlpha) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.combatHistory = new RingBuffer<>(historySize);
        this.aimErrorWindow = new Stats.RollingWindow(windowSize);
        this.snapAngleWindow = new Stats.RollingWindow(windowSize);
        this.reachWindow = new Stats.RollingWindow(windowSize);
        this.attackIntervalWindow = new Stats.RollingWindow(windowSize);
        this.hitRateWindow = new Stats.RollingWindow(windowSize);
        this.aimErrorEwma = new Stats.EWMA(ewmaAlpha);
        this.snapAngleEwma = new Stats.EWMA(ewmaAlpha);
        this.reachEwma = new Stats.EWMA(ewmaAlpha);
        this.attackIntervalEwma = new Stats.EWMA(ewmaAlpha);
        this.totalAttacks = 0;
        this.totalHits = 0;
        this.totalCriticals = 0;
        this.lastAttackNanos = 0;
        this.lastAlertNanos = 0;
        this.exemptUntilNanos = 0;
        this.recentHits = 0;
        this.recentAttacks = 0;
    }
    
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    
    // History access
    public RingBuffer<CombatInput> getCombatHistory() { return combatHistory; }
    public Stats.RollingWindow getAimErrorWindow() { return aimErrorWindow; }
    public Stats.RollingWindow getSnapAngleWindow() { return snapAngleWindow; }
    public Stats.RollingWindow getReachWindow() { return reachWindow; }
    public Stats.RollingWindow getAttackIntervalWindow() { return attackIntervalWindow; }
    public Stats.RollingWindow getHitRateWindow() { return hitRateWindow; }
    
    // EWMA access
    public Stats.EWMA getAimErrorEwma() { return aimErrorEwma; }
    public Stats.EWMA getSnapAngleEwma() { return snapAngleEwma; }
    public Stats.EWMA getReachEwma() { return reachEwma; }
    public Stats.EWMA getAttackIntervalEwma() { return attackIntervalEwma; }
    
    // Statistics
    public int getTotalAttacks() { return totalAttacks; }
    public int getTotalHits() { return totalHits; }
    public int getTotalCriticals() { return totalCriticals; }
    public long getLastAttackNanos() { return lastAttackNanos; }
    public long getLastAlertNanos() { return lastAlertNanos; }
    public void setLastAlertNanos(long nanos) { this.lastAlertNanos = nanos; }
    
    public long getExemptUntilNanos() { return exemptUntilNanos; }
    public void setExemptUntilNanos(long nanos) { this.exemptUntilNanos = nanos; }
    
    public int getRecentHits() { return recentHits; }
    public int getRecentAttacks() { return recentAttacks; }
    
    public UUID getLastTargetId() { return lastTargetId; }
    public int getConsecutiveTargetHits() { return consecutiveTargetHits; }
    
    /**
     * Checks if combat checks are currently exempt.
     * 
     * @param currentNanos Current monotonic time
     * @return true if exempt
     */
    public boolean isExempt(long currentNanos) {
        return currentNanos < exemptUntilNanos;
    }
    
    /**
     * Adds a combat input to history and updates statistics.
     * 
     * @param input Combat input
     */
    public void addCombatInput(CombatInput input) {
        combatHistory.push(input);
        totalAttacks++;
        recentAttacks++;
        
        // Update hit tracking
        if (input.hit()) {
            totalHits++;
            recentHits++;
            if (input.critical()) {
                totalCriticals++;
            }
            
            // Update aim statistics for hits only (more reliable)
            double aimError = input.aimError();
            aimErrorWindow.add(aimError);
            aimErrorEwma.update(aimError);
            
            // Track reach for hits
            double reach = input.distanceToTarget();
            reachWindow.add(reach);
            reachEwma.update(reach);
        }
        
        // Update snap angle
        double snapAngle = input.snapAngle();
        snapAngleWindow.add(snapAngle);
        snapAngleEwma.update(snapAngle);
        
        // Update attack interval
        if (lastAttackNanos > 0 && input.nanoTime() > lastAttackNanos) {
            double intervalMs = (input.nanoTime() - lastAttackNanos) / 1_000_000.0;
            attackIntervalWindow.add(intervalMs);
            attackIntervalEwma.update(intervalMs);
        }
        
        // Track consecutive hits on same target
        if (input.hit() && input.targetId() != null) {
            if (input.targetId().equals(lastTargetId)) {
                consecutiveTargetHits++;
            } else {
                consecutiveTargetHits = 1;
                lastTargetId = input.targetId();
            }
        }
        
        // Update rolling hit rate (1 = hit, 0 = miss)
        hitRateWindow.add(input.hit() ? 1.0 : 0.0);
        
        lastAttackNanos = input.nanoTime();
    }
    
    /**
     * Gets the current hit rate from the rolling window.
     * 
     * @return Hit rate (0.0 to 1.0)
     */
    public double getRecentHitRate() {
        return hitRateWindow.mean();
    }
    
    /**
     * Gets the total hit rate.
     * 
     * @return Hit rate (0.0 to 1.0)
     */
    public double getTotalHitRate() {
        return totalAttacks > 0 ? (double) totalHits / totalAttacks : 0.0;
    }
    
    /**
     * Gets the critical hit rate.
     * 
     * @return Critical rate (0.0 to 1.0)
     */
    public double getCriticalRate() {
        return totalHits > 0 ? (double) totalCriticals / totalHits : 0.0;
    }
    
    /**
     * Resets recent statistics (called periodically).
     */
    public void resetRecentStats() {
        recentHits = 0;
        recentAttacks = 0;
    }
    
    /**
     * Clears all history and resets state.
     */
    public void reset() {
        combatHistory.clear();
        aimErrorWindow.clear();
        snapAngleWindow.clear();
        reachWindow.clear();
        attackIntervalWindow.clear();
        hitRateWindow.clear();
        aimErrorEwma.reset();
        snapAngleEwma.reset();
        reachEwma.reset();
        attackIntervalEwma.reset();
        totalAttacks = 0;
        totalHits = 0;
        totalCriticals = 0;
        lastAttackNanos = 0;
        lastAlertNanos = 0;
        recentHits = 0;
        recentAttacks = 0;
        consecutiveTargetHits = 0;
        lastTargetId = null;
    }
}
