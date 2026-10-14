package it.buonacaccia.repository;

import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.Notification;
import it.buonacaccia.model.NotificationStatus;
import it.buonacaccia.model.NotificationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Registro degli avvisi (feature 004). */
public interface NotificationRepository extends MongoRepository<Notification, String> {

    Optional<Notification> findByDomainEventIdAndRecipientIdAndTemplate(String domainEventId, String recipientId,
                                                                        NotificationTemplate template);

    List<Notification> findByDomainEventIdAndStatus(String domainEventId, NotificationStatus status);

    List<Notification> findByEnrollmentIdOrderByCreatedAtAsc(String enrollmentId);
}
