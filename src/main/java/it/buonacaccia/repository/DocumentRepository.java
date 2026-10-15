package it.buonacaccia.repository;

import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.Document;
import it.buonacaccia.model.DocumentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Documenti caricati (feature 006). */
public interface DocumentRepository extends MongoRepository<Document, String> {

    List<Document> findByEnrollmentIdOrderByUploadedAtAsc(String enrollmentId);

    Optional<Document> findByEnrollmentIdAndCodeAndStatus(String enrollmentId, String code, DocumentStatus status);
}
