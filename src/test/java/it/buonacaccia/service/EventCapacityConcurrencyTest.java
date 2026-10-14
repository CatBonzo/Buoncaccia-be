package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import it.buonacaccia.AbstractTest;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.model.EventCapacity;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.service.EventCapacityService.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** SC-002 e research R1: posti e ordine di arrivo assegnati in modo atomico anche in parallelo. */
class EventCapacityConcurrencyTest extends AbstractTest {

    private static final int REQUESTS = 200;
    private static final int SEATS = 50;

    @Autowired
    private EventCapacityService capacityService;

    @Test
    void parallelReservationsGetUniqueArrivalsWithoutGaps() throws Exception {
        String eventId = UUID.randomUUID().toString();
        List<Reservation> reservations = reserveInParallel(eventId);

        assertThat(reservations.stream().mapToLong(Reservation::arrival).sorted().toArray())
                .containsExactly(LongStream.rangeClosed(1, REQUESTS).toArray());
        assertThat(reservations.stream().filter(r -> r.position() == SeatPosition.IN_SEATS)).hasSize(SEATS);
        // I primi SEATS arrivati sono nei posti, gli altri in attesa (BR-07).
        assertThat(reservations.stream().filter(r -> r.position() == SeatPosition.IN_SEATS)
                .mapToLong(Reservation::arrival).max().orElseThrow()).isEqualTo(SEATS);

        EventCapacity capacity = capacityService.find(eventId).orElseThrow();
        assertThat(capacity.getLastArrival()).isEqualTo(REQUESTS);
        assertThat(capacity.getInSeats()).isEqualTo(SEATS);
        assertThat(capacity.getWaiting()).isEqualTo(REQUESTS - SEATS);
        assertThat(capacity.getActive()).isEqualTo(REQUESTS);
    }

    @Test
    void freedSeatGoesToWaitingBeforeNewcomers() {
        String eventId = UUID.randomUUID().toString();
        capacityService.reserve(eventId, 1);
        capacityService.reserve(eventId, 1);

        capacityService.release(eventId, SeatPosition.IN_SEATS);
        // Il posto libero spetta a chi aspetta (BR-08): un nuovo arrivato va in attesa.
        assertThat(capacityService.reserve(eventId, 1).position()).isEqualTo(SeatPosition.WAITING);

        assertThat(capacityService.takeFreeSeat(eventId)).isTrue();
        assertThat(capacityService.takeFreeSeat(eventId)).isFalse();
        EventCapacity capacity = capacityService.find(eventId).orElseThrow();
        assertThat(capacity.getInSeats()).isEqualTo(1);
        assertThat(capacity.getWaiting()).isEqualTo(1);
        assertThat(capacity.getActive()).isEqualTo(2);
    }

    @Test
    void seatsCannotDropBelowAssigned() {
        String eventId = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            capacityService.reserve(eventId, 3);
        }

        assertThatThrownBy(() -> capacityService.updateSeats(eventId, 2))
                .isInstanceOfSatisfying(BuonaCacciaException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SEATS_BELOW_ASSIGNED));
        capacityService.updateSeats(eventId, 5);
        assertThat(capacityService.find(eventId).orElseThrow().getSeats()).isEqualTo(5);
        assertThat(capacityService.takeFreeSeat(eventId)).isFalse(); // nessuno in attesa
    }

    private List<Reservation> reserveInParallel(String eventId) throws Exception {
        List<Callable<Reservation>> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            tasks.add(() -> capacityService.reserve(eventId, SEATS));
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            List<Reservation> result = new ArrayList<>();
            for (Future<Reservation> future : pool.invokeAll(tasks)) {
                result.add(future.get());
            }
            return result;
        }
    }
}
