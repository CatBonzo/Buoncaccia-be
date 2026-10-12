package it.buonacaccia.utils;

/**
 * Catalogo dei codici di errore dell'envelope. Formato {@code SSSNN}: {@code SSS} è lo status HTTP,
 * {@code NN} il dettaglio (research R5). 0 indica il successo.
 */
public enum ErrorCode {

    UNAUTHENTICATED(40100, 401, "AUTH", "Autenticazione richiesta: token assente o non valido."),
    FORBIDDEN(40300, 403, "AUTH", "Permesso mancante per questa operazione."),
    NOT_FOUND(40400, 404, "SYSTEM", "Risorsa non trovata."),
    METHOD_NOT_ALLOWED(40500, 405, "SYSTEM", "Metodo HTTP non supportato per questa risorsa."),
    CONFLICT(40900, 409, "SYSTEM", "Conflitto con lo stato attuale della risorsa."),
    INVALID_INPUT(42200, 422, "SYSTEM", "Richiesta non valida."),
    INTERNAL_ERROR(50000, 500, "SYSTEM", "Errore interno. Riprova più tardi.");

    private final int code;
    private final int httpStatus;
    private final String domain;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String domain, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.domain = domain;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String domain() {
        return domain;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
