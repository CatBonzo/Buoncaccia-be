package it.buonacaccia.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.model.Availability;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventLifecycle;
import it.buonacaccia.model.EventStatus;
import org.springframework.stereotype.Component;

/**
 * Stato effettivo e indicatore di disponibilità di un evento, calcolati dall'ora (research R1).
 * Gli istanti si calcolano una volta, al salvataggio, dalle date nell'ora italiana: così il cambio
 * dell'ora legale è gestito da {@code ZonedDateTime} e lo stato è sempre coerente (FR-013, FR-014).
 * Un evento che è iniziato non ha più le iscrizioni aperte, anche se la chiusura era lo stesso giorno.
 */
@Component
public class EventStatusResolver {

    /** Ora di apertura delle iscrizioni (BR-03). */
    public static final LocalTime OPENING_TIME = LocalTime.of(9, 0);

    /** Soglie dell'indicatore in percentuale dei posti (BR-14). */
    static final int LIMITED_FROM_PERCENT = 90;
    static final int HEAVY_OVERBOOKING_ABOVE_PERCENT = 120;

    /** Ricalcola i quattro istanti dalle date; quelli delle date mancanti restano vuoti. */
    public void computeInstants(Event event) {
        event.setRegistrationOpensAt(atTime(event.getRegistrationOpenDate(), OPENING_TIME));
        event.setRegistrationClosesAt(endOfDay(event.getRegistrationCloseDate()));
        event.setStartsAt(startOfDay(event.getStartDate()));
        event.setEndsAt(endOfDay(event.getEndDate()));
    }

    public EventStatus status(Event event, Instant now) {
        if (event.getLifecycle() == EventLifecycle.DRAFT) {
            return EventStatus.DRAFT;
        }
        if (event.getLifecycle() == EventLifecycle.CANCELLED) {
            return EventStatus.CANCELLED;
        }
        if (now.isBefore(event.getRegistrationOpensAt())) {
            return EventStatus.PUBLISHED;
        }
        if (!now.isBefore(event.getStartsAt())) {
            return now.isAfter(event.getEndsAt()) ? EventStatus.COMPLETED : EventStatus.IN_PROGRESS;
        }
        if (!now.isAfter(event.getRegistrationClosesAt()) && event.getRegistrationClosedEarlyAt() == null) {
            return EventStatus.REGISTRATION_OPEN;
        }
        return EventStatus.REGISTRATION_CLOSED;
    }

    public Availability availability(Event event, Instant now) {
        return switch (status(event, now)) {
            case PUBLISHED -> Availability.NOT_YET_OPEN;
            case REGISTRATION_OPEN -> byOccupancy(event.getEnrolledCount(), event.getSeats());
            default -> Availability.CLOSED;
        };
    }

    static Availability byOccupancy(int enrolled, int seats) {
        long percentTimesSeats = 100L * enrolled;
        if (percentTimesSeats < (long) LIMITED_FROM_PERCENT * seats) {
            return Availability.OPEN;
        }
        if (percentTimesSeats <= (long) HEAVY_OVERBOOKING_ABOVE_PERCENT * seats) {
            return Availability.LIMITED;
        }
        return Availability.HEAVY_OVERBOOKING;
    }

    private static Instant atTime(LocalDate date, LocalTime time) {
        return date == null ? null : date.atTime(time).atZone(ClockConfig.ROME).toInstant();
    }

    private static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ClockConfig.ROME).toInstant();
    }

    private static Instant endOfDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(ClockConfig.ROME).toInstant().minusMillis(1);
    }
}
