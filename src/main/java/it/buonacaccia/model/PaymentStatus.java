package it.buonacaccia.model;

/** Un pagamento annullato non si cancella: resta nel registro con il motivo (FR-006 della 005). */
public enum PaymentStatus {
    VALID, CANCELLED
}
