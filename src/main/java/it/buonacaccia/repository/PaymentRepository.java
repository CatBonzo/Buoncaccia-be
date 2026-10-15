package it.buonacaccia.repository;

import java.util.List;

import it.buonacaccia.model.Payment;
import it.buonacaccia.model.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Pagamenti registrati (feature 005). */
public interface PaymentRepository extends MongoRepository<Payment, String> {

    List<Payment> findByEnrollmentIdOrderByRegisteredAtAsc(String enrollmentId);

    List<Payment> findByEnrollmentIdAndStatus(String enrollmentId, PaymentStatus status);

    List<Payment> findByEventIdOrderByRegisteredAtAsc(String eventId);
}
