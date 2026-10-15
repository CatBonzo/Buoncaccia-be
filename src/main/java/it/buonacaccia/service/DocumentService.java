package it.buonacaccia.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.Document;
import it.buonacaccia.model.DocumentDelivery;
import it.buonacaccia.model.DocumentStatus;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.repository.DocumentRepository;
import it.buonacaccia.repository.EnrollmentRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EnrollmentAccessPolicy;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documenti caricati per gli adempimenti dell'iscrizione (feature 006). Il contenuto va nello
 * {@link DocumentStorage} prima della transazione (GridFS non vi partecipa, research R1); la
 * transazione registra metadati, sostituzione del precedente e stato dell'adempimento. Due
 * caricamenti insieme si contendono la versione dell'iscrizione: perde chi arriva dopo e riprova una volta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int HEAD_BYTES = 16;

    private final DocumentRepository documentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentAccessPolicy enrollmentAccessPolicy;
    private final EventAccessPolicy eventAccessPolicy;
    private final DocumentStorage storage;
    private final MongoTemplate mongoTemplate;
    private final CircuitBreakerService circuitBreakerService;
    private final TransactionRunner transactions;
    private final Clock clock;

    /**
     * @throws BuonaCacciaException {@code NOT_FOUND} per chi non è partecipante o presentatore o per un codice
     *                              sconosciuto, {@code ENROLLMENT_STATE_CONFLICT} se l'iscrizione è chiusa o
     *                              il documento è cartaceo o già validato, {@code INVALID_INPUT} (campo
     *                              {@code file}) per file vuoto, troppo grande o di tipo non ammesso
     */
    public Document upload(CallerContext caller, String enrollmentId, String code, MultipartFile file) {
        byte[] content = read(file);
        return transactions.retryOnConflict(() -> uploadOnce(caller, enrollmentId, code, file, content));
    }

    private Document uploadOnce(CallerContext caller, String enrollmentId, String code, MultipartFile file, byte[] content) {
        Enrollment enrollment = find(enrollmentId);
        if (!enrollmentAccessPolicy.isParticipantOrPresenter(caller, enrollment)) {
            throw notFound("Iscrizione non trovata.");
        }
        EnrollmentObligation obligation = documentObligation(enrollment, code);
        if (!enrollment.isActive()) {
            throw conflict("L'iscrizione non è attiva: non si caricano più documenti.");
        }
        if (obligation.getDelivery() == DocumentDelivery.PAPER) {
            throw conflict("Il documento " + code + " si consegna in cartaceo: non si carica online.");
        }
        if (obligation.getStatus() != ObligationStatus.TODO && obligation.getStatus() != ObligationStatus.IN_REVIEW) {
            throw conflict("Il documento " + code + " è già stato validato.");
        }
        String contentType = checkFile(obligation, content);
        Instant now = clock.instant();
        String key = storage.store(file.getOriginalFilename(), contentType, content);
        Document document = Document.builder()
                .id(UUID.randomUUID().toString())
                .enrollmentId(enrollment.getId())
                .enrollmentNumber(enrollment.getNumber())
                .eventId(enrollment.getEventId())
                .eventCode(enrollment.getEventCode())
                .eventLevel(enrollment.getEventLevel())
                .eventRegion(enrollment.getEventRegion())
                .participantId(enrollment.getParticipantId())
                .code(code)
                .originalName(safeName(file.getOriginalFilename()))
                .contentType(contentType)
                .sizeBytes(content.length)
                .sha256(sha256(content))
                .storageKey(key)
                .status(DocumentStatus.UPLOADED)
                .uploadedBy(caller.getSubject())
                .uploadedAt(now)
                .build();
        obligation.setStatus(ObligationStatus.IN_REVIEW);
        obligation.setNote(null);
        enrollment.setUpdatedAt(now);
        try {
            transactions.run(() -> {
                mongoTemplate.updateMulti(
                        Query.query(Criteria.where("enrollmentId").is(enrollment.getId()).and("code").is(code)
                                .and("status").is(DocumentStatus.UPLOADED)),
                        new Update().set("status", DocumentStatus.REPLACED), Document.class);
                documentRepository.insert(document);
                return enrollmentRepository.save(enrollment);
            });
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        log.info("Iscrizione {}: documento {} caricato da {}", enrollment.getNumber(), code, caller.getSubject());
        return document;
    }

    // ---------- consultazione ----------

    /** Contenuto e metadati di un documento scaricato. */
    public record DocumentContent(Document document, byte[] content) {
    }

    /** Documenti di un'iscrizione, anche sostituiti e rifiutati, per partecipante, presentatore e segreteria (FR-013). */
    public List<Document> list(CallerContext caller, String enrollmentId) {
        Enrollment enrollment = find(enrollmentId);
        checkCanSee(caller, enrollment);
        return mongo(() -> documentRepository.findByEnrollmentIdOrderByUploadedAtAsc(enrollmentId));
    }

    /** Scarico del file: per gli altri il documento non esiste (404). */
    public DocumentContent content(CallerContext caller, String documentId) {
        Document document = findDocument(documentId);
        checkCanSee(caller, find(document.getEnrollmentId()));
        try (var in = storage.open(document.getStorageKey())) {
            return new DocumentContent(document, in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Contenuto del documento " + documentId + " illeggibile", e);
        }
    }

    private void checkCanSee(CallerContext caller, Enrollment enrollment) {
        boolean allowed = enrollmentAccessPolicy.isParticipantOrPresenter(caller, enrollment)
                || eventAccessPolicy.canManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
        if (!allowed) {
            throw notFound("Documento non trovato.");
        }
    }

    private Document findDocument(String id) {
        return mongo(() -> documentRepository.findById(id)).orElseThrow(() -> notFound("Documento non trovato."));
    }

    // ---------- controlli sul file ----------

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw invalid("Il file non si è potuto leggere.");
        }
    }

    /** Vuoto, troppo grande o di un tipo non ammesso: 422 sul campo {@code file}. Restituisce il tipo rilevato. */
    private static String checkFile(EnrollmentObligation obligation, byte[] content) {
        if (content.length == 0) {
            throw invalid("Il file è vuoto.");
        }
        long max = DocumentRules.maxBytesOf(obligation.getMaxSizeBytes());
        if (content.length > max) {
            throw invalid("Il file supera la dimensione massima di " + max + " byte.");
        }
        String detected = FileTypeDetector.detect(Arrays.copyOf(content, Math.min(content.length, HEAD_BYTES)))
                .orElseThrow(() -> invalid("Formato non riconosciuto: sono ammessi JPEG, PNG e PDF."));
        List<String> allowed = DocumentRules.typesOf(obligation.getAllowedMimeTypes());
        if (!allowed.contains(detected)) {
            throw invalid("Il formato " + detected + " non è ammesso per questo documento. Ammessi: " + String.join(", ", allowed) + ".");
        }
        return detected;
    }

    private static EnrollmentObligation documentObligation(Enrollment enrollment, String code) {
        return enrollment.getObligations().stream()
                .filter(o -> o.getType() == ObligationType.DOCUMENT && code.equals(o.getCode()))
                .findFirst()
                .orElseThrow(() -> notFound("Documento " + code + " non richiesto da questo evento."));
    }

    /** Nome del file senza percorso né caratteri di controllo, al massimo 120 caratteri. */
    static String safeName(String name) {
        String base = name == null ? "documento" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (base.isEmpty()) {
            base = "documento";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("File orfano {} non eliminato: {}", key, e.getMessage());
        }
    }

    // ---------- accesso ai dati ----------

    private Enrollment find(String id) {
        return mongo(() -> enrollmentRepository.findById(id)).orElseThrow(() -> notFound("Iscrizione non trovata."));
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }

    private static BuonaCacciaException notFound(String message) {
        return new BuonaCacciaException(ErrorCode.NOT_FOUND, message);
    }

    private static BuonaCacciaException conflict(String message) {
        return new BuonaCacciaException(ErrorCode.ENROLLMENT_STATE_CONFLICT, message);
    }

    private static BuonaCacciaException invalid(String message) {
        return new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                List.of(new FieldErrorDTO("file", message)));
    }
}
