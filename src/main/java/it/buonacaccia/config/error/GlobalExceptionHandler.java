package it.buonacaccia.config.error;

import java.util.List;

import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Traduce ogni eccezione nell'envelope con lo status HTTP del catalogo (FR-013…FR-015). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BuonaCacciaException.class)
    ResponseEntity<ApiResponse<Object>> handle(BuonaCacciaException e) {
        log.debug("Errore applicativo {}: {}", e.errorCode(), e.getMessage());
        return respond(e.errorCode(), e.getMessage(), e.payload());
    }

    /** Due modifiche concorrenti: la seconda, basata su una versione superata, è rifiutata (FR-018). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Object>> handle(OptimisticLockingFailureException e) {
        return respond(ErrorCode.STALE_VERSION);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Object>> handle(AuthenticationException e) {
        return respond(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Object>> handle(AccessDeniedException e) {
        return respond(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Object>> handle(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Object>> handle(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** Corpo non valido: {@code payload} = un elemento per campo (research R7). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> handle(MethodArgumentNotValidException e) {
        List<FieldErrorDTO> fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDTO(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(), fields);
    }

    /** Parametro della richiesta di tipo sbagliato, es. una data non valida: {@code payload} lo indica. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Object>> handle(MethodArgumentTypeMismatchException e) {
        List<FieldErrorDTO> fields = List.of(new FieldErrorDTO(e.getName(), "Valore non valido: " + e.getValue()));
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(), fields);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Object>> handleInvalidInput(Exception e) {
        log.debug("Richiesta non valida: {}", e.getMessage());
        return respond(ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception e) {
        log.error("Errore imprevisto", e);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ApiResponse<Object>> respond(ErrorCode error) {
        return respond(error, error.defaultMessage());
    }

    private static ResponseEntity<ApiResponse<Object>> respond(ErrorCode error, String message) {
        return respond(error, message, null);
    }

    private static ResponseEntity<ApiResponse<Object>> respond(ErrorCode error, String message, Object payload) {
        return ResponseEntity.status(error.httpStatus()).body(ApiResponse.error(error, message, payload));
    }
}
