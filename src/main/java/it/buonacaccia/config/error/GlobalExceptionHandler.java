package it.buonacaccia.config.error;

import it.buonacaccia.v1.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
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
    ResponseEntity<ApiResponse<Void>> handle(BuonaCacciaException e) {
        log.debug("Errore applicativo {}: {}", e.errorCode(), e.getMessage());
        return respond(e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> handle(AuthenticationException e) {
        return respond(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handle(AccessDeniedException e) {
        return respond(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handle(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handle(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception e) {
        return respond(ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Errore imprevisto", e);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode error) {
        return respond(error, error.defaultMessage());
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode error, String message) {
        return ResponseEntity.status(error.httpStatus()).body(ApiResponse.error(error, message));
    }
}
