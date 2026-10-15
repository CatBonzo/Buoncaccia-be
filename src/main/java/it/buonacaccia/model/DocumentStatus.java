package it.buonacaccia.model;

/** Stati di un documento caricato (feature 006): {@code UPLOADED → VALIDATED | REJECTED}, oppure {@code REPLACED}. */
public enum DocumentStatus {
    UPLOADED, VALIDATED, REJECTED, REPLACED
}
