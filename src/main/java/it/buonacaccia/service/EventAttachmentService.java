package it.buonacaccia.service;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventAttachment;
import it.buonacaccia.model.EventLifecycle;
import it.buonacaccia.repository.EventAttachmentRepository;
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * File che la segreteria allega a un evento (programma, regolamento, moduli). Si vedono e si scaricano
 * senza accedere quando l'evento è pubblicato; la bozza li mostra solo a chi gestisce l'evento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAttachmentService {

    static final int MAX_PER_EVENT = 10;
    static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int HEAD_BYTES = 16;

    private final EventAttachmentRepository attachmentRepository;
    private final EventRepository eventRepository;
    private final EventAccessPolicy accessPolicy;
    private final DocumentStorage storage;
    private final CircuitBreakerService circuitBreakerService;
    private final Clock clock;

    /** Contenuto e metadati di un allegato scaricato. */
    public record AttachmentContent(EventAttachment attachment, byte[] content) {
    }

    public List<EventAttachment> list(CallerContext caller, String eventId) {
        visibleEvent(caller, eventId);
        return mongo(() -> attachmentRepository.findByEventIdOrderByUploadedAtAsc(eventId));
    }

    public AttachmentContent content(CallerContext caller, String eventId, String attachmentId) {
        visibleEvent(caller, eventId);
        EventAttachment attachment = find(eventId, attachmentId);
        try (var in = storage.open(attachment.getStorageKey())) {
            return new AttachmentContent(attachment, in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Allegato " + attachmentId + " illeggibile", e);
        }
    }

    public EventAttachment upload(CallerContext caller, String eventId, MultipartFile file) {
        Event event = visibleEvent(caller, eventId);
        accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        if (event.getLifecycle() == EventLifecycle.CANCELLED) {
            throw new BuonaCacciaException(ErrorCode.STATE_CONFLICT, "L'evento è annullato: non si allegano più file.");
        }
        byte[] content = read(file);
        if (content.length == 0) {
            throw invalid("Il file è vuoto.");
        }
        if (content.length > MAX_BYTES) {
            throw invalid("Il file supera la dimensione massima di " + MAX_BYTES + " byte.");
        }
        String type = FileTypeDetector.detect(Arrays.copyOf(content, Math.min(content.length, HEAD_BYTES)))
                .orElseThrow(() -> invalid("Formato non riconosciuto: sono ammessi JPEG, PNG e PDF."));
        if (mongo(() -> attachmentRepository.countByEventId(eventId)) >= MAX_PER_EVENT) {
            throw new BuonaCacciaException(ErrorCode.STATE_CONFLICT,
                    "Un evento può avere al massimo " + MAX_PER_EVENT + " allegati.");
        }
        String name = DocumentService.safeName(file.getOriginalFilename());
        String key = storage.store(name, type, content);
        EventAttachment attachment = EventAttachment.builder()
                .id(UUID.randomUUID().toString())
                .eventId(eventId)
                .name(name)
                .contentType(type)
                .sizeBytes(content.length)
                .storageKey(key)
                .uploadedBy(caller.getSubject())
                .uploadedAt(clock.instant())
                .build();
        try {
            mongo(() -> attachmentRepository.save(attachment));
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        log.info("Evento {}: allegato {} caricato da {}", eventId, name, caller.getSubject());
        return attachment;
    }

    public void delete(CallerContext caller, String eventId, String attachmentId) {
        Event event = visibleEvent(caller, eventId);
        accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        EventAttachment attachment = find(eventId, attachmentId);
        mongo(() -> {
            attachmentRepository.deleteById(attachmentId);
            return null;
        });
        deleteQuietly(attachment.getStorageKey());
    }

    /** Toglie tutti gli allegati di un evento eliminato. */
    public void deleteAllOf(String eventId) {
        for (EventAttachment attachment : mongo(() -> attachmentRepository.findByEventIdOrderByUploadedAtAsc(eventId))) {
            mongo(() -> {
                attachmentRepository.deleteById(attachment.getId());
                return null;
            });
            deleteQuietly(attachment.getStorageKey());
        }
    }

    // ---------- aiuti ----------

    /** L'evento esiste e il chiamante lo può vedere: la bozza è invisibile a chi non la gestisce. */
    private Event visibleEvent(CallerContext caller, String eventId) {
        Event event = mongo(() -> eventRepository.findById(eventId)).orElseThrow(EventAttachmentService::notFound);
        if (event.getLifecycle() == EventLifecycle.DRAFT
                && (caller == null || !accessPolicy.canManage(caller, event.getLevel(), event.getRegion()))) {
            throw notFound();
        }
        return event;
    }

    private EventAttachment find(String eventId, String attachmentId) {
        return mongo(() -> attachmentRepository.findById(attachmentId))
                .filter(a -> a.getEventId().equals(eventId))
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Allegato non trovato."));
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw invalid("Il file non si è potuto leggere.");
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("File orfano {} non eliminato: {}", key, e.getMessage());
        }
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }

    private static BuonaCacciaException notFound() {
        return new BuonaCacciaException(ErrorCode.NOT_FOUND, "Evento non trovato.");
    }

    private static BuonaCacciaException invalid(String message) {
        return new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                List.of(new FieldErrorDTO("file", message)));
    }
}
