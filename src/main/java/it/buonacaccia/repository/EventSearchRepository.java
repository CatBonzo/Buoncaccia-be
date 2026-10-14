package it.buonacaccia.repository;

import java.time.Instant;
import java.util.List;

import it.buonacaccia.model.Availability;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventLifecycle;
import it.buonacaccia.model.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Ricerca degli eventi con {@code MongoTemplate} (research R6). Lo stato effettivo non è salvato:
 * si traduce in condizioni sugli istanti all'ora indicata, le stesse di {@code EventStatusResolver}
 * (un evento iniziato non ha più le iscrizioni aperte).
 */
@Repository
@RequiredArgsConstructor
public class EventSearchRepository {

    private static final String LIFECYCLE = "lifecycle";
    private static final String OPENS_AT = "registrationOpensAt";
    private static final String CLOSES_AT = "registrationClosesAt";
    private static final String CLOSED_EARLY_AT = "registrationClosedEarlyAt";
    private static final String STARTS_AT = "startsAt";
    private static final String ENDS_AT = "endsAt";

    private final MongoTemplate mongoTemplate;

    /** Una pagina degli eventi che soddisfano tutte le condizioni. */
    public Page<Event> search(List<Criteria> conditions, Pageable pageable) {
        Query query = conditions.isEmpty() ? new Query() : new Query(new Criteria().andOperator(conditions));
        long total = mongoTemplate.count(query, Event.class);
        List<Event> content = mongoTemplate.find(Query.of(query).with(pageable), Event.class);
        return new PageImpl<>(content, pageable, total);
    }

    /** Condizione per lo stato effettivo all'istante {@code now}. */
    public static Criteria status(EventStatus status, Instant now) {
        return switch (status) {
            case DRAFT -> Criteria.where(LIFECYCLE).is(EventLifecycle.DRAFT);
            case CANCELLED -> Criteria.where(LIFECYCLE).is(EventLifecycle.CANCELLED);
            case PUBLISHED -> published().and(OPENS_AT).gt(now);
            case REGISTRATION_OPEN -> published().and(OPENS_AT).lte(now).and(STARTS_AT).gt(now)
                    .and(CLOSES_AT).gte(now).and(CLOSED_EARLY_AT).is(null);
            case REGISTRATION_CLOSED -> published().and(OPENS_AT).lte(now).and(STARTS_AT).gt(now)
                    .orOperator(Criteria.where(CLOSES_AT).lt(now), Criteria.where(CLOSED_EARLY_AT).ne(null));
            case IN_PROGRESS -> published().and(STARTS_AT).lte(now).and(ENDS_AT).gte(now);
            case COMPLETED -> published().and(ENDS_AT).lt(now);
        };
    }

    /** Condizione per l'indicatore di disponibilità all'istante {@code now} (BR-14). */
    public static Criteria availability(Availability availability, Instant now) {
        return switch (availability) {
            case NOT_YET_OPEN -> status(EventStatus.PUBLISHED, now);
            case OPEN -> new Criteria().andOperator(status(EventStatus.REGISTRATION_OPEN, now),
                    Criteria.expr(ComparisonOperators.valueOf(enrolledPercent()).lessThan(seatsTimes(90))));
            case LIMITED -> new Criteria().andOperator(status(EventStatus.REGISTRATION_OPEN, now),
                    Criteria.expr(ComparisonOperators.valueOf(enrolledPercent()).greaterThanEqualTo(seatsTimes(90))),
                    Criteria.expr(ComparisonOperators.valueOf(enrolledPercent()).lessThanEqualTo(seatsTimes(120))));
            case HEAVY_OVERBOOKING -> new Criteria().andOperator(status(EventStatus.REGISTRATION_OPEN, now),
                    Criteria.expr(ComparisonOperators.valueOf(enrolledPercent()).greaterThan(seatsTimes(120))));
            case CLOSED -> new Criteria().orOperator(
                    status(EventStatus.CANCELLED, now),
                    status(EventStatus.REGISTRATION_CLOSED, now),
                    published().and(STARTS_AT).lte(now));
        };
    }

    private static Criteria published() {
        return Criteria.where(LIFECYCLE).is(EventLifecycle.PUBLISHED);
    }

    private static AggregationExpression enrolledPercent() {
        return ArithmeticOperators.valueOf("enrolledCount").multiplyBy(100);
    }

    private static AggregationExpression seatsTimes(int percent) {
        return ArithmeticOperators.valueOf("seats").multiplyBy(percent);
    }
}
