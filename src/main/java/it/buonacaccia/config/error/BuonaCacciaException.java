package it.buonacaccia.config.error;


/**
 * Errore applicativo con il suo codice del catalogo; il gestore globale lo traduce in envelope.
 * Il {@code payload} facoltativo finisce nell'envelope, es. l'elenco dei campi da correggere (research R7).
 */
public class BuonaCacciaException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object payload;

    public BuonaCacciaException(ErrorCode errorCode, String message, Object payload) {
        super(message);
        this.errorCode = errorCode;
        this.payload = payload;
    }

    public BuonaCacciaException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BuonaCacciaException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object payload() {
        return payload;
    }
}
