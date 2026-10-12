package it.buonacaccia.v1.dto;

import it.buonacaccia.config.error.ErrorCode;

/**
 * Envelope di ogni risposta (FR-013): {@code errorCode} 0 = successo e dati in {@code payload};
 * altrimenti codice, messaggio e dominio dell'errore, con {@code payload} vuoto.
 */
public record ApiResponse<T>(int errorCode, String errorMessage, String errorDomain, T payload) {

    public static <T> ApiResponse<T> ok(T payload) {
        return new ApiResponse<>(0, null, null, payload);
    }

    public static <T> ApiResponse<T> error(ErrorCode error) {
        return error(error, error.defaultMessage());
    }

    public static <T> ApiResponse<T> error(ErrorCode error, String message) {
        return new ApiResponse<>(error.code(), message, error.domain(), null);
    }
}
