package it.buonacaccia.config.mongo;

import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import it.buonacaccia.service.exception.BuonaCacciaException;
import it.buonacaccia.utils.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Circuit breaker sulle chiamate a MongoDB (Resilience4j), sul modello di ScoutShopper-be.
 * I guasti del database contano per l'apertura del circuito; a circuito aperto le chiamate non
 * partono e la risposta è 503. Configurazione in {@code application.yml}, istanza {@code mongodb}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerService {

    public static final String MONGODB = "mongodb";

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Esegue l'operazione dentro il circuit breaker indicato.
     *
     * @throws BuonaCacciaException {@code SERVICE_UNAVAILABLE} se il database non risponde o il
     *                              circuito è aperto, {@code CONFLICT} per una chiave duplicata
     */
    public <T> T execute(String circuitBreakerName, Supplier<T> operation) {
        try {
            return circuitBreakerRegistry.circuitBreaker(circuitBreakerName).executeSupplier(operation);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker {} aperto: chiamata non eseguita", circuitBreakerName);
            throw new BuonaCacciaException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (DuplicateKeyException e) {
            log.warn("Chiave duplicata su {}: {}", circuitBreakerName, e.getMessage());
            throw new BuonaCacciaException(ErrorCode.CONFLICT);
        } catch (DataAccessException e) {
            log.error("Errore di accesso a {}", circuitBreakerName, e);
            throw new BuonaCacciaException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
