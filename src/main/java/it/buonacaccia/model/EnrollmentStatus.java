package it.buonacaccia.model;

/** Stato dell'iscrizione (feature 003, data-model). Attive: {@code REGISTERED} e {@code COMPLETED}. */
public enum EnrollmentStatus {
    REGISTERED, COMPLETED, EXPIRED, WITHDRAWN, REJECTED, CANCELLED;

    public boolean isActive() {
        return this == REGISTERED || this == COMPLETED;
    }
}
