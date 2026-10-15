package it.buonacaccia.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.Refund;
import it.buonacaccia.model.RefundStatus;
import it.buonacaccia.notification.DomainEventRecorder;
import it.buonacaccia.repository.RefundRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.dto.RefundIssueDTO;
import it.buonacaccia.v1.dto.RefundPageDTO;
import it.buonacaccia.v1.mapper.RefundMapper;
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

/**
 * Rimborsi (feature 005, research R6 e R7): nascono nella transazione che chiude l'iscrizione e la
 * segreteria li lavora con transizioni condizionate sullo stato di partenza.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RefundRepository refundRepository;
    private final MongoTemplate mongoTemplate;
    private final EventAccessPolicy eventAccessPolicy;
    private final DomainEventRecorder domainEvents;
    private final TransactionRunner transactions;
    private final CircuitBreakerService circuitBreakerService;
    private final Clock clock;

    /**
     * Apre il rimborso di un'iscrizione appena chiusa, se qualcosa è stato versato e il diritto c'è.
     * Va chiamato dentro la transazione di chiusura (R6).
     */
    public void requestFor(Enrollment enrollment, Event event) {
        if (enrollment.getPaidCents() <= 0 || !Boolean.TRUE.equals(enrollment.getRefundDue())
                || refundRepository.existsByEnrollmentId(enrollment.getId())) {
            return;
        }
        Refund refund = Refund.builder()
                .id(UUID.randomUUID().toString())
                .enrollmentId(enrollment.getId())
                .enrollmentNumber(enrollment.getNumber())
                .participantId(enrollment.getParticipantId())
                .participantName(enrollment.getParticipantName())
                .presenterId(enrollment.getPresenterId())
                .eventId(enrollment.getEventId())
                .eventCode(enrollment.getEventCode())
                .eventLevel(enrollment.getEventLevel())
                .eventRegion(enrollment.getEventRegion())
                .amountCents(enrollment.getPaidCents())
                .closingReason(enrollment.getClosingReason())
                .status(RefundStatus.REQUESTED)
                .createdAt(clock.instant())
                .build();
        refundRepository.insert(refund);
        if (event != null) {
            domainEvents.refund(DomainEventType.REFUND_REQUESTED, refund, enrollment, event);
        }
        log.info("Iscrizione {}: rimborso di {} centesimi richiesto", enrollment.getNumber(), refund.getAmountCents());
    }

    /** Il rimborso di un'iscrizione, se esiste. */
    public java.util.Optional<Refund> ofEnrollment(String enrollmentId) {
        return mongo(() -> refundRepository.findByEnrollmentId(enrollmentId));
    }

    /** Coda dei rimborsi della propria regione (tutti per l'amministrazione), dal più vecchio. */
    public RefundPageDTO list(CallerContext caller, RefundStatus status, String eventId, Integer page, Integer size) {
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
        Criteria criteria = new Criteria();
        if (!caller.isAdmin()) {
            criteria = Criteria.where("eventRegion").is(caller.getRegion()).and("eventLevel").ne(EventLevel.NATIONAL);
        }
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        if (eventId != null && !eventId.isBlank()) {
            criteria = criteria.and("eventId").is(eventId);
        }
        Query query = new Query(criteria);
        PageRequest pageRequest = PageRequest.of(p, s, Sort.by("createdAt").and(Sort.by("id")));
        Page<Refund> result = mongo(() -> new PageImpl<>(mongoTemplate.find(Query.of(query).with(pageRequest), Refund.class),
                pageRequest, mongoTemplate.count(query, Refund.class)));
        return RefundPageDTO.builder()
                .content(RefundMapper.INSTANCE.toDtoList(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /** {@code REQUESTED → IN_PROGRESS}, nessuna e-mail: il socio non deve fare nulla (R8). */
    public Refund take(CallerContext caller, String id) {
        Instant now = clock.instant();
        return transition(caller, id, Set.of(RefundStatus.REQUESTED),
                new Update().set("status", RefundStatus.IN_PROGRESS).set("takenBy", caller.getSubject()).set("takenAt", now),
                null);
    }

    /** {@code REQUESTED|IN_PROGRESS → ISSUED}, con data (non futura) e riferimento. */
    public Refund issue(CallerContext caller, String id, RefundIssueDTO request) {
        Instant now = clock.instant();
        if (request.getIssuedOn().isAfter(now.atZone(ClockConfig.ROME).toLocalDate())) {
            throw new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                    List.of(new FieldErrorDTO("issuedOn", "La data del rimborso non può essere nel futuro.")));
        }
        String reference = request.getReference() == null || request.getReference().isBlank() ? null : request.getReference().trim();
        return transition(caller, id, Set.of(RefundStatus.REQUESTED, RefundStatus.IN_PROGRESS),
                new Update().set("status", RefundStatus.ISSUED).set("issuedOn", request.getIssuedOn())
                        .set("reference", reference).set("processedBy", caller.getSubject()).set("processedAt", now),
                DomainEventType.REFUND_ISSUED);
    }

    /** {@code REQUESTED|IN_PROGRESS → REJECTED}, con il motivo. */
    public Refund reject(CallerContext caller, String id, String reason) {
        Instant now = clock.instant();
        return transition(caller, id, Set.of(RefundStatus.REQUESTED, RefundStatus.IN_PROGRESS),
                new Update().set("status", RefundStatus.REJECTED).set("rejectReason", reason)
                        .set("processedBy", caller.getSubject()).set("processedAt", now),
                DomainEventType.REFUND_REJECTED);
    }

    /**
     * Aggiornamento condizionato sullo stato di partenza (R7): chi arriva dopo trova lo stato cambiato e
     * riceve {@code REFUND_TRANSITION}. L'evento di dominio, se c'è, è nella stessa transazione.
     */
    private Refund transition(CallerContext caller, String id, Set<RefundStatus> from, Update update, DomainEventType event) {
        Refund current = mongo(() -> refundRepository.findById(id))
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Rimborso non trovato."));
        if (!eventAccessPolicy.canManage(caller, current.getEventLevel(), current.getEventRegion())) {
            throw new BuonaCacciaException(ErrorCode.NOT_FOUND, "Rimborso non trovato.");
        }
        Query query = new Query(Criteria.where("id").is(id).and("status").in(from));
        Refund updated = transactions.retryOnConflict(() -> transactions.run(() -> {
            Refund refund = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Refund.class);
            if (refund != null && event != null) {
                Enrollment enrollment = mongoTemplate.findById(refund.getEnrollmentId(), Enrollment.class);
                Event ofEvent = mongoTemplate.findById(refund.getEventId(), Event.class);
                if (enrollment != null && ofEvent != null) {
                    domainEvents.refund(event, refund, enrollment, ofEvent);
                }
            }
            return refund;
        }));
        if (updated == null) {
            throw new BuonaCacciaException(ErrorCode.REFUND_TRANSITION);
        }
        log.info("Rimborso {} ora {} (da {})", id, updated.getStatus(), caller.getSubject());
        return updated;
    }

    private <T> T mongo(java.util.function.Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }
}
