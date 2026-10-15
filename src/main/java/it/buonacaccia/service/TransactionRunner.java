package it.buonacaccia.service;

import java.util.function.Supplier;

import com.mongodb.MongoException;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scritture in transazione con un solo riprovo sui conflitti (feature 005, research R2). Un conflitto con
 * un'altra transazione sugli stessi documenti diventa una versione superata, che il circuit breaker
 * ignora per l'apertura del circuito; chi chiama rilegge tutto e ritenta una volta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionRunner {

    private final TransactionTemplate transactionTemplate;
    private final CircuitBreakerService circuitBreakerService;

    /** Esegue le scritture in una transazione dentro il circuit breaker di MongoDB. */
    public <T> T run(Supplier<T> writes) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, () -> {
            try {
                return transactionTemplate.execute(tx -> writes.get());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (DataAccessException e) {
                if (isTransient(e)) {
                    throw new OptimisticLockingFailureException("Conflitto fra transazioni sugli stessi documenti", e);
                }
                throw e;
            }
        });
    }

    /** Ritenta una volta se l'operazione è fallita per una versione superata. */
    public <T> T retryOnConflict(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BuonaCacciaException e) {
            if (e.errorCode() != ErrorCode.STALE_VERSION) {
                throw e;
            }
            log.debug("Conflitto fra transazioni, riprovo una volta");
            return operation.get();
        }
    }

    private static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof MongoException mongo && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }
}
