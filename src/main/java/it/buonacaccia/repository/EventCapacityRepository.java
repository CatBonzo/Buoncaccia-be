package it.buonacaccia.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.EventCapacity;
import it.buonacaccia.model.SeatPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Operazioni atomiche sulla capienza di un evento (research R1 della 003). Ogni metodo è un solo
 * aggiornamento condizionato su un documento: MongoDB lo esegue per intero o per niente, quindi i
 * contatori non si contraddicono anche con molte richieste in parallelo.
 */
@Repository
@RequiredArgsConstructor
public class EventCapacityRepository {

    private static final String SEATS = "seats";
    private static final String LAST_ARRIVAL = "lastArrival";
    private static final String IN_SEATS = "inSeats";
    private static final String WAITING = "waiting";
    private static final String ACTIVE = "active";

    private final MongoTemplate mongoTemplate;

    /** Crea il documento se manca, con i posti indicati; se c'è già non lo tocca. */
    public void ensure(String eventId, int seats) {
        Update update = new Update().setOnInsert(SEATS, seats).setOnInsert(LAST_ARRIVAL, 0L)
                .setOnInsert(IN_SEATS, 0).setOnInsert(WAITING, 0).setOnInsert(ACTIVE, 0);
        try {
            mongoTemplate.upsert(byId(eventId), update, EventCapacity.class);
        } catch (DuplicateKeyException e) {
            // Due upsert insieme: l'altro l'ha appena creato, va bene così.
        }
    }

    /** Nuovo arrivo nei posti, solo se c'è un posto libero e nessuno aspetta (BR-08); vuoto altrimenti. */
    public Optional<EventCapacity> reserveSeat(String eventId) {
        Query query = new Query(Criteria.where("_id").is(eventId).and(WAITING).is(0)
                .andOperator(Criteria.expr(ComparisonOperators.valueOf(IN_SEATS).lessThan(SEATS))));
        Update update = new Update().inc(LAST_ARRIVAL, 1).inc(IN_SEATS, 1).inc(ACTIVE, 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(query, update, returnNew(), EventCapacity.class));
    }

    /** Nuovo arrivo in lista d'attesa. */
    public EventCapacity reserveWaiting(String eventId) {
        Update update = new Update().inc(LAST_ARRIVAL, 1).inc(WAITING, 1).inc(ACTIVE, 1);
        return mongoTemplate.findAndModify(byId(eventId), update, returnNew(), EventCapacity.class);
    }

    /** Un'iscrizione attiva esce: libera il suo posto o la sua posizione in attesa. */
    public void release(String eventId, SeatPosition position) {
        String counter = position == SeatPosition.IN_SEATS ? IN_SEATS : WAITING;
        mongoTemplate.updateFirst(byId(eventId), new Update().inc(counter, -1).inc(ACTIVE, -1), EventCapacity.class);
    }

    /** Sposta un'unità da "in attesa" a "nei posti" se c'è un posto libero e qualcuno aspetta. */
    public boolean takeFreeSeat(String eventId) {
        Query query = new Query(Criteria.where("_id").is(eventId).and(WAITING).gt(0)
                .andOperator(Criteria.expr(ComparisonOperators.valueOf(IN_SEATS).lessThan(SEATS))));
        return mongoTemplate.updateFirst(query, new Update().inc(IN_SEATS, 1).inc(WAITING, -1), EventCapacity.class)
                .getModifiedCount() == 1;
    }

    /** Annulla {@link #takeFreeSeat} quando non c'era un'iscrizione da promuovere. */
    public void giveBackFreeSeat(String eventId) {
        mongoTemplate.updateFirst(byId(eventId), new Update().inc(IN_SEATS, -1).inc(WAITING, 1), EventCapacity.class);
    }

    /**
     * Cambia i posti se non scendono sotto gli iscritti nei posti.
     *
     * @return {@code false} se il documento esiste e i posti nuovi sono troppo pochi
     */
    public boolean setSeats(String eventId, int seats) {
        Query query = new Query(Criteria.where("_id").is(eventId).and(IN_SEATS).lte(seats));
        if (mongoTemplate.updateFirst(query, new Update().set(SEATS, seats), EventCapacity.class).getMatchedCount() == 1) {
            return true;
        }
        return !mongoTemplate.exists(byId(eventId), EventCapacity.class);
    }

    /** Evento annullato: nessuna iscrizione attiva. */
    /** Toglie del tutto la capienza di un evento che viene eliminato. */
    public void delete(String eventId) {
        mongoTemplate.remove(byId(eventId), EventCapacity.class);
    }

    public void reset(String eventId) {
        mongoTemplate.updateFirst(byId(eventId), new Update().set(IN_SEATS, 0).set(WAITING, 0).set(ACTIVE, 0),
                EventCapacity.class);
    }

    public Optional<EventCapacity> findById(String eventId) {
        return Optional.ofNullable(mongoTemplate.findById(eventId, EventCapacity.class));
    }

    public List<EventCapacity> findAllById(Collection<String> eventIds) {
        return mongoTemplate.find(new Query(Criteria.where("_id").in(eventIds)), EventCapacity.class);
    }

    /** Id degli eventi con iscritti attivi almeno al {@code minPercent} dei posti (BR-14). */
    public List<String> idsWithOccupancyAtLeast(int minPercent) {
        return ids(ComparisonOperators.valueOf(activePercent()).greaterThanEqualTo(seatsTimes(minPercent)));
    }

    /** Id degli eventi con iscritti attivi oltre il {@code percent} dei posti. */
    public List<String> idsWithOccupancyAbove(int percent) {
        return ids(ComparisonOperators.valueOf(activePercent()).greaterThan(seatsTimes(percent)));
    }

    private List<String> ids(AggregationExpression condition) {
        Query query = new Query(Criteria.expr(condition));
        query.fields().include("_id");
        return mongoTemplate.find(query, EventCapacity.class).stream().map(EventCapacity::getId).toList();
    }

    private static AggregationExpression activePercent() {
        return ArithmeticOperators.valueOf(ACTIVE).multiplyBy(100);
    }

    private static AggregationExpression seatsTimes(int percent) {
        return ArithmeticOperators.valueOf(SEATS).multiplyBy(percent);
    }

    private static Query byId(String eventId) {
        return new Query(Criteria.where("_id").is(eventId));
    }

    private static FindAndModifyOptions returnNew() {
        return FindAndModifyOptions.options().returnNew(true);
    }
}
