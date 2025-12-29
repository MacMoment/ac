package com.macmoment.macac.model;

import java.util.Objects;

/**
 * Decision made by the mitigation policy indicating the action to take for a violation.
 * 
 * <p>After aggregating check results and identifying a potential violation, the
 * mitigation policy evaluates factors like exemptions, cooldowns, and thresholds
 * to produce a Decision. The decision specifies what action (if any) should be
 * taken in response.
 * 
 * <p>This is an immutable record containing:
 * <ul>
 *   <li>{@code action} - The type of action to take</li>
 *   <li>{@code violation} - The violation that triggered this decision (null for NONE)</li>
 *   <li>{@code reason} - Human-readable explanation for the decision</li>
 * </ul>
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public record Decision(
    Action action,
    Violation violation,
    String reason
) {
    
    /**
     * Action types for violation handling.
     * 
     * <p>Actions are ordered by severity, with NONE being the least severe
     * and PUNISH being the most severe.
     */
    public enum Action {
        /** No action required - violation below threshold or player is exempt. */
        NONE,
        /** Flag the violation for later review without alerting staff. */
        FLAG,
        /** Alert staff members but don't punish the player. */
        ALERT,
        /** Punish the player (kick, ban, etc.) based on configuration. */
        PUNISH
    }
    
    /**
     * Compact constructor that validates the action.
     */
    public Decision {
        Objects.requireNonNull(action, "action must not be null");
        // violation may be null for NONE decisions
        // reason may be null but is informational
    }

    /**
     * Creates a decision to take no action.
     * 
     * <p>Use this when a violation doesn't meet thresholds or the player
     * is exempt from checks.
     * 
     * @param reason explanation for why no action is taken
     * @return Decision with NONE action
     */
    public static Decision none(final String reason) {
        return new Decision(Action.NONE, null, reason);
    }

    /**
     * Creates a decision to alert staff.
     * 
     * <p>Staff members with the appropriate permission will be notified
     * of the violation but no automatic punishment occurs.
     * 
     * @param violation the violation to alert about; must not be null
     * @return Decision with ALERT action
     * @throws NullPointerException if violation is null
     */
    public static Decision alert(final Violation violation) {
        Objects.requireNonNull(violation, "violation must not be null for ALERT decision");
        return new Decision(Action.ALERT, violation, "Confidence exceeded alert threshold");
    }

    /**
     * Creates a decision to punish the player.
     * 
     * <p>This triggers both an alert and the configured punishment
     * (kick, ban, etc.) as defined in the plugin configuration.
     * 
     * @param violation the violation to punish for; must not be null
     * @return Decision with PUNISH action
     * @throws NullPointerException if violation is null
     */
    public static Decision punish(final Violation violation) {
        Objects.requireNonNull(violation, "violation must not be null for PUNISH decision");
        return new Decision(Action.PUNISH, violation, "Confidence exceeded punishment threshold");
    }

    /**
     * Creates a decision to flag for review.
     * 
     * <p>The violation is recorded for manual review but no immediate
     * action is taken. Staff members are not immediately notified.
     * 
     * @param violation the violation to flag; must not be null
     * @return Decision with FLAG action
     * @throws NullPointerException if violation is null
     */
    public static Decision flag(final Violation violation) {
        Objects.requireNonNull(violation, "violation must not be null for FLAG decision");
        return new Decision(Action.FLAG, violation, "Flagged for manual review");
    }

    /**
     * Returns true if this decision requires any action to be taken.
     * 
     * @return true if action is not NONE
     */
    public boolean requiresAction() {
        return action != Action.NONE;
    }
    
    /**
     * Returns true if this decision will notify staff.
     * 
     * @return true if action is ALERT or PUNISH
     */
    public boolean notifiesStaff() {
        return action == Action.ALERT || action == Action.PUNISH;
    }
    
    /**
     * Returns true if this decision will punish the player.
     * 
     * @return true if action is PUNISH
     */
    public boolean willPunish() {
        return action == Action.PUNISH;
    }
}
