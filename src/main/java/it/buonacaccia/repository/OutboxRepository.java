package it.buonacaccia.repository;

import java.util.List;

import it.buonacaccia.model.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Outbox degli eventi di dominio (feature 004). */
public interface OutboxRepository extends MongoRepository<OutboxEvent, String> {

    /** Al più 100 eventi da pubblicare, nell'ordine in cui sono stati scritti. */
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);

    long countByStatus(OutboxEvent.Status status);
}
