package it.buonacaccia.config.error;

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
    STATE_CONFLICT(40901, 409, "CATALOG", "Operazione non consentita nello stato attuale dell'evento."),
    EVENT_TYPE_EXISTS(40902, 409, "CATALOG", "Esiste già un tipo evento con questo codice."),
    STALE_VERSION(40903, 409, "SYSTEM", "La risorsa è stata modificata da qualcun altro: ricarica e riprova."),
    FIELD_LOCKED(40904, 409, "CATALOG", "Campo non modificabile dopo la pubblicazione."),
    SEATS_BELOW_ASSIGNED(40905, 409, "CATALOG", "I posti non possono scendere sotto gli iscritti già nei posti."),
    ENROLLMENT_STATE_CONFLICT(40910, 409, "ENROLLMENT", "Operazione non consentita nello stato attuale dell'iscrizione."),
    ALREADY_ENROLLED(40911, 409, "ENROLLMENT", "Il socio ha già un'iscrizione attiva a questo evento."),
    REGISTRATIONS_NOT_OPEN(40912, 409, "ENROLLMENT", "Le iscrizioni a questo evento non sono aperte."),
    LINK_NOT_VALID(40420, 404, "NOTIFICATION", "Il link non è valido o non lo è più."),
    DECISION_NOT_POSSIBLE(40920, 409, "NOTIFICATION", "L'autorizzazione non è più da dare."),
    PAYMENT_DUPLICATE(40930, 409, "PAYMENT", "Esiste già un pagamento uguale registrato su questa iscrizione."),
    PAYMENT_CANCELLED(40931, 409, "PAYMENT", "Il pagamento è già stato annullato."),
    REFUND_TRANSITION(40940, 409, "REFUND", "Il rimborso non può passare a questo stato."),
    DOCUMENT_TRANSITION(40960, 409, "DOCUMENT", "Il documento non è più in attesa di verifica."),
    FORM_TRANSITION(40970, 409, "FORM", "La scheda non è in uno stato che consente questa operazione."),
    INVALID_INPUT(42200, 422, "SYSTEM", "Richiesta non valida."),
    PUBLISH_INVARIANTS(42201, 422, "CATALOG", "L'evento non si può pubblicare: correggi i campi indicati."),
    NOT_ELIGIBLE(42210, 422, "ENROLLMENT", "Il socio non ha i requisiti per questo evento."),
    INTERNAL_ERROR(50000, 500, "SYSTEM", "Errore interno. Riprova più tardi."),
    SERVICE_UNAVAILABLE(50300, 503, "SYSTEM", "Servizio temporaneamente non disponibile. Riprova tra poco.");

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
