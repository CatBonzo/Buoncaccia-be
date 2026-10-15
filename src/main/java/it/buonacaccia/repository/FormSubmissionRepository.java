package it.buonacaccia.repository;

import java.util.Optional;

import it.buonacaccia.model.FormSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Schede compilate, una per iscrizione (feature 007). */
public interface FormSubmissionRepository extends MongoRepository<FormSubmission, String> {

    Optional<FormSubmission> findByEnrollmentId(String enrollmentId);
}
