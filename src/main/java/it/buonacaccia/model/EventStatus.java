package it.buonacaccia.model;

/** Stato effettivo dell'evento (FR-012), calcolato da EventStatusResolver. */
public enum EventStatus {
    DRAFT, PUBLISHED, REGISTRATION_OPEN, REGISTRATION_CLOSED, IN_PROGRESS, COMPLETED, CANCELLED
}
