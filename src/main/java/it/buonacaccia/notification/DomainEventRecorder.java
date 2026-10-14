package it.buonacaccia.notification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.OutboxEvent;
import it.buonacaccia.model.SeatPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Scrive gli eventi di dominio nell'outbox (research R1–R3 della 004). È l'unico punto in cui il
 * resto dell'applicazione tocca le notifiche. Va chiamato dentro la transazione del cambio di stato,
 * così l'evento esiste se e solo se il cambio è stato salvato (FR-002).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventRecorder {

    private static final List<ObligationType> AUTHORIZATIONS =
            List.of(ObligationType.UNIT_LEADER_AUTHORIZATION, ObligationType.GROUP_LEADER_AUTHORIZATION);

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    /** Cambio di stato di un'iscrizione. */
    public void enrollment(DomainEventType type, Enrollment enrollment, Event event) {
        write(type, enrollment.getId(), snapshot(enrollment, event), snapshot(event, null), null, null);
    }

    /** Un {@code AUTHORIZATION_REQUESTED} per ogni autorizzazione dei capi ancora da dare (FR-009). */
    public void authorizationsRequested(Enrollment enrollment, Event event) {
        for (EnrollmentObligation obligation : enrollment.getObligations()) {
            if (AUTHORIZATIONS.contains(obligation.getType()) && obligation.getStatus() == ObligationStatus.TODO) {
                write(DomainEventType.AUTHORIZATION_REQUESTED, enrollment.getId(), snapshot(enrollment, event),
                        snapshot(event, null), obligation.getType(), null);
            }
        }
    }

    /** Promemoria a {@code days} giorni dalla scadenza (FR-010). */
    public void reminder(Enrollment enrollment, Event event, int days) {
        write(DomainEventType.DEADLINE_REMINDER, enrollment.getId(), snapshot(enrollment, event), snapshot(event, null),
                null, days);
    }

    /** Date o luogo di un evento pubblicato cambiati (FR-012). */
    public void eventChanged(Event event, List<String> changedFields) {
        write(DomainEventType.EVENT_CHANGED, event.getId(), null, snapshot(event, changedFields), null, null);
    }

    private void write(DomainEventType type, String aggregateId, DomainEvent.EnrollmentSnapshot enrollment,
                       DomainEvent.EventSnapshot event, ObligationType obligationType, Integer reminderDays) {
        Instant now = clock.instant();
        DomainEvent domainEvent = new DomainEvent(UUID.randomUUID().toString(), type, now, enrollment, event,
                obligationType, reminderDays);
        mongoTemplate.insert(OutboxEvent.builder()
                .id(domainEvent.id())
                .type(type)
                .aggregateId(aggregateId)
                .payload(domainEvent)
                .status(OutboxEvent.Status.PENDING)
                .createdAt(now)
                .build());
        log.debug("Evento di dominio {} {} in outbox ({})", type, domainEvent.id(), aggregateId);
    }

    private DomainEvent.EnrollmentSnapshot snapshot(Enrollment enrollment, Event event) {
        BigDecimal fee = event.getFee();
        Integer waitingRank = null;
        if (enrollment.isActive() && enrollment.getPosition() == SeatPosition.WAITING) {
            Query ahead = new Query(Criteria.where("eventId").is(enrollment.getEventId()).and("active").is(true)
                    .and("position").is(SeatPosition.WAITING).and("arrivalOrder").lt(enrollment.getArrivalOrder()));
            waitingRank = (int) mongoTemplate.count(ahead, Enrollment.class) + 1;
        }
        List<ObligationType> pending = enrollment.getObligations().stream()
                .filter(o -> o.getStatus() == ObligationStatus.TODO)
                .map(EnrollmentObligation::getType)
                .toList();
        return new DomainEvent.EnrollmentSnapshot(enrollment.getId(), enrollment.getNumber(),
                enrollment.getParticipantId(), enrollment.getParticipantName(), enrollment.getParticipantUnitId(),
                enrollment.getParticipantGroupId(), enrollment.getPresenterId(), enrollment.getPresenterName(),
                enrollment.getStatus(), enrollment.getPosition(), waitingRank, pending, enrollment.getDeadline(),
                fee, enrollment.getRefundDue(), enrollment.getClosingReason(), enrollment.getClosingNote());
    }

    private static DomainEvent.EventSnapshot snapshot(Event event, List<String> changedFields) {
        return new DomainEvent.EventSnapshot(event.getId(), event.getCode(), event.getTitle(), event.getStartDate(),
                event.getEndDate(), event.getLocation(), changedFields);
    }
}
