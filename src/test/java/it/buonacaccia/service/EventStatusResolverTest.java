package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.model.Availability;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventLifecycle;
import it.buonacaccia.model.EventStatus;
import org.junit.jupiter.api.Test;

/** Stato effettivo dall'ora italiana (FR-013, FR-014, SC-005) e indicatore (FR-021). */
class EventStatusResolverTest {

    private final EventStatusResolver resolver = new EventStatusResolver();

    @Test
    void draftAndCancelledIgnoreTime() {
        Event draft = published("2027-03-01", "2027-03-10", "2027-03-20", "2027-03-22");
        draft.setLifecycle(EventLifecycle.DRAFT);
        Event cancelled = published("2027-03-01", "2027-03-10", "2027-03-20", "2027-03-22");
        cancelled.setLifecycle(EventLifecycle.CANCELLED);

        assertThat(resolver.status(draft, rome("2027-03-05T12:00"))).isEqualTo(EventStatus.DRAFT);
        assertThat(resolver.status(cancelled, rome("2027-03-05T12:00"))).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void followsTheCalendarInItalianTime() {
        Event event = published("2027-03-01", "2027-03-10", "2027-03-20", "2027-03-22");

        assertThat(resolver.status(event, rome("2027-03-01T08:59:59"))).isEqualTo(EventStatus.PUBLISHED);
        assertThat(resolver.status(event, rome("2027-03-01T09:00:00"))).isEqualTo(EventStatus.REGISTRATION_OPEN);
        assertThat(resolver.status(event, rome("2027-03-10T23:59:59"))).isEqualTo(EventStatus.REGISTRATION_OPEN);
        assertThat(resolver.status(event, rome("2027-03-11T00:00:00"))).isEqualTo(EventStatus.REGISTRATION_CLOSED);
        assertThat(resolver.status(event, rome("2027-03-19T23:59:59"))).isEqualTo(EventStatus.REGISTRATION_CLOSED);
        assertThat(resolver.status(event, rome("2027-03-20T00:00:00"))).isEqualTo(EventStatus.IN_PROGRESS);
        assertThat(resolver.status(event, rome("2027-03-22T23:59:59"))).isEqualTo(EventStatus.IN_PROGRESS);
        assertThat(resolver.status(event, rome("2027-03-23T00:00:00"))).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    void opensAtNineOnDaylightSavingDays() {
        // 28/03/2027: inizia l'ora legale (02:00 → 03:00); 31/10/2027: torna l'ora solare.
        Event spring = published("2027-03-28", "2027-04-10", "2027-04-20", "2027-04-22");
        Event autumn = published("2027-10-31", "2027-11-10", "2027-11-20", "2027-11-22");

        assertThat(spring.getRegistrationOpensAt()).isEqualTo(Instant.parse("2027-03-28T07:00:00Z"));
        assertThat(autumn.getRegistrationOpensAt()).isEqualTo(Instant.parse("2027-10-31T08:00:00Z"));
        assertThat(resolver.status(spring, rome("2027-03-28T08:59:59"))).isEqualTo(EventStatus.PUBLISHED);
        assertThat(resolver.status(spring, rome("2027-03-28T09:00:00"))).isEqualTo(EventStatus.REGISTRATION_OPEN);
        assertThat(resolver.status(autumn, rome("2027-10-31T08:59:59"))).isEqualTo(EventStatus.PUBLISHED);
        assertThat(resolver.status(autumn, rome("2027-10-31T09:00:00"))).isEqualTo(EventStatus.REGISTRATION_OPEN);
    }

    @Test
    void closingOnDepartureDayStopsAtDeparture() {
        Event event = published("2027-03-01", "2027-03-20", "2027-03-20", "2027-03-22");

        assertThat(resolver.status(event, rome("2027-03-19T23:00"))).isEqualTo(EventStatus.REGISTRATION_OPEN);
        assertThat(resolver.status(event, rome("2027-03-20T10:00"))).isEqualTo(EventStatus.IN_PROGRESS);
    }

    @Test
    void earlyClosingClosesRegistrations() {
        Event event = published("2027-03-01", "2027-03-10", "2027-03-20", "2027-03-22");
        event.setRegistrationClosedEarlyAt(rome("2027-03-05T10:00"));

        assertThat(resolver.status(event, rome("2027-03-05T12:00"))).isEqualTo(EventStatus.REGISTRATION_CLOSED);
        assertThat(resolver.availability(event, 0, rome("2027-03-05T12:00"))).isEqualTo(Availability.CLOSED);
    }

    @Test
    void availabilityThresholds() {
        assertThat(EventStatusResolver.byOccupancy(89, 100)).isEqualTo(Availability.OPEN);
        assertThat(EventStatusResolver.byOccupancy(90, 100)).isEqualTo(Availability.LIMITED);
        assertThat(EventStatusResolver.byOccupancy(120, 100)).isEqualTo(Availability.LIMITED);
        assertThat(EventStatusResolver.byOccupancy(121, 100)).isEqualTo(Availability.HEAVY_OVERBOOKING);
    }

    @Test
    void availabilityFollowsStatus() {
        Event event = published("2027-03-01", "2027-03-10", "2027-03-20", "2027-03-22");

        assertThat(resolver.availability(event, 0, rome("2027-02-01T12:00"))).isEqualTo(Availability.NOT_YET_OPEN);
        assertThat(resolver.availability(event, 0, rome("2027-03-05T12:00"))).isEqualTo(Availability.OPEN);
        assertThat(resolver.availability(event, 0, rome("2027-03-21T12:00"))).isEqualTo(Availability.CLOSED);
    }

    private Event published(String open, String close, String start, String end) {
        Event event = Event.builder()
                .lifecycle(EventLifecycle.PUBLISHED)
                .registrationOpenDate(LocalDate.parse(open))
                .registrationCloseDate(LocalDate.parse(close))
                .startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end))
                .seats(100)
                .build();
        resolver.computeInstants(event);
        return event;
    }

    private static Instant rome(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ClockConfig.ROME).toInstant();
    }
}
