package it.buonacaccia.repository;

import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.Refund;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Rimborsi (feature 005). */
public interface RefundRepository extends MongoRepository<Refund, String> {

    Optional<Refund> findByEnrollmentId(String enrollmentId);

    boolean existsByEnrollmentId(String enrollmentId);

    List<Refund> findByEventIdOrderByCreatedAtAsc(String eventId);
}
