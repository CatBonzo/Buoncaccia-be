package it.buonacaccia.service;

import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.Counter;
import it.buonacaccia.model.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Codice leggibile dell'evento {@code PIE-2027-001}, {@code NAZ} per i nazionali (research R5).
 * Il progressivo è un {@code findAndModify} atomico sulla collezione {@code counters}: niente
 * duplicati anche con richieste contemporanee.
 */
@Service
@RequiredArgsConstructor
public class EventCodeGenerator {

    static final String NATIONAL_PREFIX = "NAZ";

    private final MongoTemplate mongoTemplate;
    private final CircuitBreakerService circuitBreakerService;

    public String next(Region region, int year) {
        String key = (region == null ? NATIONAL_PREFIX : region.name()) + "-" + year;
        Counter counter = circuitBreakerService.execute(CircuitBreakerService.MONGODB, () -> mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(key)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Counter.class));
        return "%s-%03d".formatted(key, counter.getSeq());
    }
}
