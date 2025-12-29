package com.macmoment.macac.model;

/**
 * Decision made by the mitigation policy.
 * Indicates what action should be taken for a violation.
 */
public record Decision(
    Action action,
    Violation violation,
    String reason
) {
    /**
     * Action types for violation handling.
     */
    public enum Action {
        /** No action - violation below threshold or exempt */
        NONE,
        /** Alert staff only */
        ALERT,
        /** Punish the player */
        PUNISH,
        /** Flag for review without immediate action */
        FLAG
    }

    /**
     * Creates a decision to take no action.
     * 
     * @param reason Reason for no action
     * @return Decision with NONE action
     */
    public static Decision none(String reason) {
        return new Decision(Action.NONE, null, reason);
    }

    /**
     * Creates a decision to alert staff.
     * 
     * @param violation The violation
     * @return Decision with ALERT action
     */
    public static Decision alert(Violation violation) {
        return new Decision(Action.ALERT, violation, "Confidence exceeded alert threshold");
    }

    /**
     * Creates a decision to punish.
     * 
     * @param violation The violation
     * @return Decision with PUNISH action
     */
    public static Decision punish(Violation violation) {
        return new Decision(Action.PUNISH, violation, "Confidence exceeded punishment threshold");
    }

    /**
     * Creates a decision to flag for review.
     * 
     * @param violation The violation
     * @return Decision with FLAG action
     */
    public static Decision flag(Violation violation) {
        return new Decision(Action.FLAG, violation, "Flagged for manual review");
    }

    /**
     * Returns true if this decision requires any action.
     * 
     * @return true if action is not NONE
     */
    public boolean requiresAction() {
        return action != Action.NONE;
    }
}
