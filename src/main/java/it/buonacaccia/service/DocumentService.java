package it.buonacaccia.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.notification.DomainEventRecorder;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.Event;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EnrollmentAccessPolicy;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.v1.dto.DocumentPageDTO;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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

    private static final int MAX_PAGE_SIZE = 100;
    private static final int HEAD_BYTES = 16;
    /** Dopo un rifiuto il socio ha almeno questo tempo per ricaricare, anche se la scadenza è passata mentre aspettava. */
    private static final Duration MIN_TIME_AFTER_REJECTION = Duration.ofDays(3);

    private final DocumentRepository documentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentAccessPolicy enrollmentAccessPolicy;
    private final EventAccessPolicy eventAccessPolicy;
    private final EventRepository eventRepository;
    private final DomainEventRecorder domainEvents;
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
        enrollmentAccessPolicy.requireAct(caller, enrollment);
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

    // ---------- verifica ----------

    /** La segreteria valida un documento in verifica: l'adempimento è soddisfatto (FR-008). */
    public Document validate(CallerContext caller, String documentId) {
        return transactions.retryOnConflict(() -> review(caller, documentId, null));
    }

    /** La segreteria rifiuta un documento in verifica con un motivo: l'adempimento torna da fare (FR-009). */
    public Document reject(CallerContext caller, String documentId, String reason) {
        return transactions.retryOnConflict(() -> review(caller, documentId, reason));
    }

    /**
     * @param reason null per validare, il motivo per rifiutare
     * @throws BuonaCacciaException {@code FORBIDDEN} fuori dalla propria regione, {@code DOCUMENT_TRANSITION}
     *                              se il documento non è più in verifica, {@code ENROLLMENT_STATE_CONFLICT}
     *                              se l'iscrizione è chiusa
     */
    private Document review(CallerContext caller, String documentId, String reason) {
        Document document = findDocument(documentId);
        eventAccessPolicy.checkCanManage(caller, document.getEventLevel(), document.getEventRegion());
        Enrollment enrollment = find(document.getEnrollmentId());
        if (document.getStatus() != DocumentStatus.UPLOADED) {
            throw new BuonaCacciaException(ErrorCode.DOCUMENT_TRANSITION);
        }
        if (!enrollment.isActive()) {
            throw conflict("L'iscrizione non è attiva.");
        }
        EnrollmentObligation obligation = documentObligation(enrollment, document.getCode());
        Event event = mongo(() -> eventRepository.findById(enrollment.getEventId())).orElse(null);
        boolean approve = reason == null;
        Instant now = clock.instant();
        Update update = new Update().set("status", approve ? DocumentStatus.VALIDATED : DocumentStatus.REJECTED)
                .set("reviewedBy", caller.getSubject()).set("reviewedAt", now);
        if (!approve) {
            update.set("rejectionReason", reason);
        }
        obligation.setStatus(approve ? ObligationStatus.SATISFIED : ObligationStatus.TODO);
        obligation.setSatisfiedBy(approve ? caller.getSubject() : null);
        obligation.setSatisfiedAt(approve ? now : null);
        obligation.setNote(approve ? null : reason);
        DomainEventType completed = EnrollmentService.completeIfDone(enrollment, now);
        if (!approve && enrollment.getDeadline() != null
                && enrollment.getDeadline().isBefore(now.plus(MIN_TIME_AFTER_REJECTION))) {
            enrollment.setDeadline(now.plus(MIN_TIME_AFTER_REJECTION));
        }
        Document updated = transactions.run(() -> {
            Document changed = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("id").is(documentId).and("status").is(DocumentStatus.UPLOADED)),
                    update, FindAndModifyOptions.options().returnNew(true), Document.class);
            if (changed == null) {
                throw new BuonaCacciaException(ErrorCode.DOCUMENT_TRANSITION);
            }
            Enrollment saved = enrollmentRepository.save(enrollment);
            if (event != null && completed != null) {
                domainEvents.enrollment(completed, saved, event);
            }
            if (event != null && !approve) {
                domainEvents.documentRejected(saved, event, document.getCode(), obligation.getDescription(), reason);
            }
            return changed;
        });
        log.info("Iscrizione {}: documento {} {} da {}", enrollment.getNumber(), document.getCode(),
                approve ? "validato" : "rifiutato", caller.getSubject());
        return updated;
    }

    // ---------- coda ----------

    /**
     * Coda dei documenti della propria regione (tutti per l'amministrazione), dal più vecchio; senza stato
     * i documenti in verifica (FR-014).
     */
    public DocumentPageDTO queue(CallerContext caller, DocumentStatus status, String eventId, Integer page, Integer size) {
        eventAccessPolicy.checkManagesSomething(caller);
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        List<FieldErrorDTO> errors = new ArrayList<>();
        if (p < 0) {
            errors.add(new FieldErrorDTO("page", "La pagina parte da 0."));
        }
        if (s < 1 || s > MAX_PAGE_SIZE) {
            errors.add(new FieldErrorDTO("size", "Da 1 a " + MAX_PAGE_SIZE + " risultati per pagina."));
        }
        if (!errors.isEmpty()) {
            throw new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(), errors);
        }
        Criteria criteria = Criteria.where("status").is(status == null ? DocumentStatus.UPLOADED : status);
        if (!caller.isAdmin()) {
            criteria = criteria.and("eventRegion").is(caller.getRegion()).and("eventLevel").ne(EventLevel.NATIONAL);
        }
        if (eventId != null && !eventId.isBlank()) {
            criteria = criteria.and("eventId").is(eventId);
        }
        Query query = new Query(criteria);
        PageRequest pageRequest = PageRequest.of(p, s, Sort.by("uploadedAt").and(Sort.by("id")));
        Page<Document> result = mongo(() -> new PageImpl<>(mongoTemplate.find(Query.of(query).with(pageRequest), Document.class),
                pageRequest, mongoTemplate.count(query, Document.class)));
        return DocumentPageDTO.builder()
                .content(DocumentMapper.INSTANCE.toDtoList(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
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
        boolean allowed = enrollmentAccessPolicy.canSee(caller, enrollment)
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
