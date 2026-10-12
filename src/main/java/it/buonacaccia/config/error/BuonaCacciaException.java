package it.buonacaccia.config.error;


/** Errore applicativo con il suo codice del catalogo; il gestore globale lo traduce in envelope. */
public class BuonaCacciaException extends RuntimeException {

    private final ErrorCode errorCode;

    public BuonaCacciaException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BuonaCacciaException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
