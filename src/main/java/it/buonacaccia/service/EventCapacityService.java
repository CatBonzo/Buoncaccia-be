package it.buonacaccia.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.EventCapacity;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.repository.EventCapacityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Posti, lista d'attesa e ordine di arrivo di ogni evento (FR-005, FR-013, FR-014, research R1).
 * Le prime iscrizioni per ordine di arrivo stanno nei posti, le altre in attesa; un posto libero
 * spetta prima a chi aspetta, poi ai nuovi arrivati.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCapacityService {

    /** Ordine di arrivo e posizione assegnati a una nuova iscrizione. */
    public record Reservation(long arrival, SeatPosition position) {
    }

    private final EventCapacityRepository repository;
    private final CircuitBreakerService circuitBreakerService;

    /** Prepara la capienza alla pubblicazione dell'evento. */
    public void ensure(String eventId, int seats) {
        mongo(() -> {
            repository.ensure(eventId, seats);
            return null;
        });
    }

    /** Assegna arrivo e posizione a una nuova iscrizione: nei posti se possibile, altrimenti in attesa. */
    public Reservation reserve(String eventId, int seats) {
        return mongo(() -> {
            repository.ensure(eventId, seats);
            return repository.reserveSeat(eventId)
                    .map(capacity -> new Reservation(capacity.getLastArrival(), SeatPosition.IN_SEATS))
                    .orElseGet(() -> new Reservation(repository.reserveWaiting(eventId).getLastArrival(),
                            SeatPosition.WAITING));
        });
    }

    /** Un'iscrizione attiva esce. Se era nei posti, chi chiama deve poi promuovere il primo in attesa. */
    public void release(String eventId, SeatPosition position) {
        mongo(() -> {
            repository.release(eventId, position);
            return null;
        });
    }

    /** Riserva un posto libero per il primo in attesa; {@code false} se non c'è posto o nessuno aspetta. */
    public boolean takeFreeSeat(String eventId) {
        return mongo(() -> repository.takeFreeSeat(eventId));
    }

    /** Restituisce un posto riservato con {@link #takeFreeSeat} che non è servito. */
    public void giveBackFreeSeat(String eventId) {
        mongo(() -> {
            repository.giveBackFreeSeat(eventId);
            return null;
        });
    }

    /** Nuovo numero di posti (FR-014); per un evento senza capienza (bozza) non fa nulla. */
    public void updateSeats(String eventId, int seats) {
        if (!mongo(() -> repository.setSeats(eventId, seats))) {
            throw new BuonaCacciaException(ErrorCode.SEATS_BELOW_ASSIGNED);
        }
        log.info("Posti dell'evento {} portati a {}", eventId, seats);
    }

    /** Un evento eliminato non lascia la sua capienza. */
    public void delete(String eventId) {
        mongo(() -> {
            repository.delete(eventId);
            return null;
        });
    }

    public void reset(String eventId) {
        mongo(() -> {
            repository.reset(eventId);
            return null;
        });
    }

    public Optional<EventCapacity> find(String eventId) {
        return mongo(() -> repository.findById(eventId));
    }

    /** Iscrizioni attive per evento; gli eventi senza capienza non compaiono (= 0). */
    public Map<String, Integer> activeCounts(Collection<String> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return mongo(() -> repository.findAllById(eventIds)).stream()
                .collect(Collectors.toMap(EventCapacity::getId, EventCapacity::getActive));
    }

    public List<String> idsWithOccupancyAtLeast(int percent) {
        return mongo(() -> repository.idsWithOccupancyAtLeast(percent));
    }

    public List<String> idsWithOccupancyAbove(int percent) {
        return mongo(() -> repository.idsWithOccupancyAbove(percent));
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }
}
