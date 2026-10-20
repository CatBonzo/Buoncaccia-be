package it.buonacaccia.repository;

import java.util.List;

import it.buonacaccia.model.EventAttachment;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Allegati degli eventi. */
public interface EventAttachmentRepository extends MongoRepository<EventAttachment, String> {

    List<EventAttachment> findByEventIdOrderByUploadedAtAsc(String eventId);

    long countByEventId(String eventId);
}
