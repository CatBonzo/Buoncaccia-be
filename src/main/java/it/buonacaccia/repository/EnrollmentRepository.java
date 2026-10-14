package it.buonacaccia.repository;

import java.time.Instant;
import java.util.List;

import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EnrollmentRepository extends MongoRepository<Enrollment, String> {

    boolean existsByEventIdAndParticipantIdAndActiveTrue(String eventId, String participantId);

    boolean existsByParticipantIdAndEventTypeCodeAndActiveTrue(String participantId, String eventTypeCode);

    long countByEventIdAndParticipantGroupIdAndActiveTrue(String eventId, String participantGroupId);

    Page<Enrollment> findByParticipantId(String participantId, Pageable pageable);

    Page<Enrollment> findByPresenterId(String presenterId, Pageable pageable);

    List<Enrollment> findByEventIdAndActiveTrue(String eventId);

    List<Enrollment> findByStatusAndDeadlineBefore(EnrollmentStatus status, Instant deadline);
}
