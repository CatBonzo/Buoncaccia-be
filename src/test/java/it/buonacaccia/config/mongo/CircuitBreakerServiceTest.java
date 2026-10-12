package it.buonacaccia.config.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import it.buonacaccia.service.exception.BuonaCacciaException;
import it.buonacaccia.utils.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

/** Il circuit breaker su MongoDB: si apre dopo i guasti e risponde 503 senza chiamare il database. */
class CircuitBreakerServiceTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreakerService service;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .ignoreExceptions(DuplicateKeyException.class)
                .build());
        service = new CircuitBreakerService(registry);
    }

    @Test
    void successReturnsTheValue() {
        assertThat(service.execute(CircuitBreakerService.MONGODB, () -> "ok")).isEqualTo("ok");
    }

    @Test
    void databaseFailureIsServiceUnavailable() {
        assertThatThrownBy(() -> service.execute(CircuitBreakerService.MONGODB, this::fail))
                .isInstanceOfSatisfying(BuonaCacciaException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void openCircuitStopsCallingTheDatabase() {
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.execute(CircuitBreakerService.MONGODB, this::fail));
        }
        assertThat(registry.circuitBreaker(CircuitBreakerService.MONGODB).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> service.execute(CircuitBreakerService.MONGODB, calls::incrementAndGet))
                .isInstanceOfSatisfying(BuonaCacciaException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        assertThat(calls).hasValue(0);
    }

    @Test
    void duplicateKeyIsAConflictAndDoesNotOpenTheCircuit() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.execute(CircuitBreakerService.MONGODB, () -> {
                throw new DuplicateKeyException("chiave duplicata");
            })).isInstanceOfSatisfying(BuonaCacciaException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        }
        assertThat(registry.circuitBreaker(CircuitBreakerService.MONGODB).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private String fail() {
        throw new DataAccessResourceFailureException("MongoDB non raggiungibile");
    }
}
