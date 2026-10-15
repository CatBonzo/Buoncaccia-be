package it.buonacaccia.model;

/**
 * Stati del rimborso (FR-010 della 005): {@code REQUESTED → IN_PROGRESS → ISSUED}, oppure
 * {@code REJECTED}; {@code ISSUED} e {@code REJECTED} sono finali.
 */
public enum RefundStatus {
    REQUESTED, IN_PROGRESS, ISSUED, REJECTED
}
