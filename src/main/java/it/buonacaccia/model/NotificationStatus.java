package it.buonacaccia.model;

/**
 * Stato di un avviso (FR-006 della 004). {@code NO_ADDRESS}: il destinatario non ha e-mail;
 * {@code NO_RECIPIENT}: nessun destinatario trovato (es. nessun capo per l'autorizzazione).
 */
public enum NotificationStatus {
    PENDING, SENT, FAILED, NO_ADDRESS, NO_RECIPIENT;

    /** Avviso chiuso: alla riconsegna dello stesso evento non si rimanda (research R4). */
    public boolean isDone() {
        return this == SENT || this == NO_ADDRESS || this == NO_RECIPIENT;
    }
}
