package it.buonacaccia.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventLifecycle;
import it.buonacaccia.model.EventStatus;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.model.User;
import it.buonacaccia.repository.EnrollmentRepository;
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.repository.UserRepository;
import it.buonacaccia.service.EventCapacityService.Reservation;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EnrollmentAccessPolicy;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.EnrollmentDTO;
import it.buonacaccia.v1.dto.EnrollmentObligationDTO;
import it.buonacaccia.v1.dto.EnrollmentPageDTO;
import it.buonacaccia.v1.dto.EnrollmentRequestDTO;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.mapper.EnrollmentMapper;
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
 * Iscrizioni e lista d'attesa (feature 003): iscrizione autonoma o del capo, pagina di stato,
 * autorizzazioni e adempimenti, ritiro, scadenze, promozioni, elenchi e annullamento con l'evento.
 * Posti e ordine di arrivo li assegna {@link EventCapacityService}; ogni uscita di un'iscrizione
 * nei posti promuove il primo in attesa (BR-08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final String NUMBER_FORMAT = "%s-%04d";

    private final EnrollmentRepository enrollmentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final EventCapacityService capacityService;
    private final EnrollmentEligibility eligibility;
    private final EventStatusResolver statusResolver;
    private final EventAccessPolicy eventAccessPolicy;
    private final EnrollmentAccessPolicy accessPolicy;
    private final CircuitBreakerService circuitBreakerService;
    private final Clock clock;

    // ---------- iscrizione ----------

    /**
     * Iscrive chi chiama (FR-001) o, con {@code memberCode}, un ragazzo della sua unità (FR-002).
     * Controlli nell'ordine: evento visibile, iscrizioni aperte, relazione capo-ragazzo, già iscritto,
     * requisiti; poi posto o attesa in modo atomico (FR-005).
     */
    public EnrollmentDTO enroll(CallerContext caller, String eventId, EnrollmentRequestDTO request) {
        Event event = publishedEvent(eventId);
        Instant now = clock.instant();
        if (statusResolver.status(event, now) != EventStatus.REGISTRATION_OPEN) {
            throw new BuonaCacciaException(ErrorCode.REGISTRATIONS_NOT_OPEN);
        }
        User callerUser = callerUser(caller);
        boolean byLeader = request != null && request.getMemberCode() != null && !request.getMemberCode().isBlank();
        User participant;
        User presenter = null;
        if (byLeader) {
            participant = mongo(() -> userRepository.findByCensusMemberCode(request.getMemberCode().trim()))
                    .orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Socio non trovato."));
            checkUnitLeaderOf(callerUser, participant);
            presenter = callerUser;
        } else {
            participant = callerUser != null ? callerUser : User.builder().id(caller.getSubject()).build();
        }
        String participantId = participant.getId();
        if (mongo(() -> enrollmentRepository.existsByEventIdAndParticipantIdAndActiveTrue(eventId, participantId))) {
            throw new BuonaCacciaException(ErrorCode.ALREADY_ENROLLED);
        }
        eligibility.check(participant, byLeader, event, now);

        Reservation reservation = capacityService.reserve(eventId, event.getSeats());
        String presenterId = presenter == null ? null : presenter.getId();
        List<EnrollmentObligation> obligations = ObligationPlanner.initial(event, reservation.position(), presenterId, now);
        EnrollmentStatus status = ObligationPlanner.statusFor(obligations);
        Enrollment toInsert = Enrollment.builder()
                .id(UUID.randomUUID().toString())
                .number(NUMBER_FORMAT.formatted(event.getCode(), reservation.arrival()))
                .eventId(eventId)
                .eventCode(event.getCode())
                .eventTypeCode(event.getTypeCode())
                .eventLevel(event.getLevel())
                .eventRegion(event.getRegion())
                .participantId(participantId)
                .participantMemberCode(participant.getCensus().getMemberCode())
                .participantName(fullName(participant))
                .participantGroupId(participant.getCensus().getGroupId())
                .participantUnitId(participant.getCensus().getUnitId())
                .presenterId(presenterId)
                .presenterName(presenter == null ? null : fullName(presenter))
                .status(status)
                .position(reservation.position())
                .arrivalOrder(reservation.arrival())
                .active(true)
                .obligations(new ArrayList<>(obligations))
                .registeredAt(now)
                .deadline(ObligationPlanner.deadlineFrom(now))
                .completedAt(status == EnrollmentStatus.COMPLETED ? now : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Enrollment enrollment;
        try {
            enrollment = mongo(() -> enrollmentRepository.insert(toInsert));
        } catch (BuonaCacciaException e) {
            // Due richieste identiche nello stesso istante: vince la prima, la seconda restituisce il posto (R3).
            capacityService.release(eventId, reservation.position());
            if (reservation.position() == SeatPosition.IN_SEATS) {
                promoteWaiting(event);
            }
            throw e.errorCode() == ErrorCode.CONFLICT ? new BuonaCacciaException(ErrorCode.ALREADY_ENROLLED) : e;
        }
        log.info("Iscrizione {} ({}, {}) di {} all'evento {}{}", enrollment.getNumber(), enrollment.getPosition(),
                enrollment.getStatus(), participantId, event.getCode(), presenterId == null ? "" : " presentata da " + presenterId);
        return toDto(caller, callerUser, enrollment, event, now);
    }

    // ---------- consultazione ----------

    /** Pagina di stato (FR-021): per chi non la può vedere l'iscrizione non esiste. */
    public EnrollmentDTO get(CallerContext caller, String id) {
        Enrollment enrollment = find(id);
        User callerUser = callerUser(caller);
        if (!accessPolicy.canView(caller, callerUser, enrollment)) {
            throw notFound();
        }
        return toDto(caller, callerUser, enrollment, eventRepositoryFind(enrollment.getEventId()), clock.instant());
    }

    /** Le mie iscrizioni, dalla più recente (FR-022). */
    public EnrollmentPageDTO mine(CallerContext caller, Integer page, Integer size) {
        PageRequest pageRequest = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "registeredAt"));
        return toPage(caller, mongo(() -> enrollmentRepository.findByParticipantId(caller.getSubject(), pageRequest)));
    }

    /** Iscrizioni presentate dal capo, dalla più recente (FR-022). */
    public EnrollmentPageDTO presented(CallerContext caller, Integer page, Integer size) {
        PageRequest pageRequest = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "registeredAt"));
        return toPage(caller, mongo(() -> enrollmentRepository.findByPresenterId(caller.getSubject(), pageRequest)));
    }

    /** Iscrizioni attive con un'autorizzazione da dare da parte del chiamante (capo unità o capo gruppo). */
    public EnrollmentPageDTO toAuthorize(CallerContext caller, Integer page, Integer size) {
        PageRequest pageRequest = pageRequest(page, size, Sort.by(Sort.Direction.ASC, "registeredAt"));
        User callerUser = callerUser(caller);
        List<Criteria> alternatives = new ArrayList<>();
        if (callerUser != null && callerUser.getCensus() != null) {
            var census = callerUser.getCensus();
            if (census.getMemberType() == MemberType.LEADER && census.getUnitId() != null) {
                alternatives.add(Criteria.where("participantUnitId").is(census.getUnitId())
                        .and("obligations").elemMatch(todo(ObligationType.UNIT_LEADER_AUTHORIZATION)));
            }
            if (census.isGroupLeader() && census.getGroupId() != null) {
                alternatives.add(Criteria.where("participantGroupId").is(census.getGroupId())
                        .and("obligations").elemMatch(todo(ObligationType.GROUP_LEADER_AUTHORIZATION)));
            }
        }
        if (alternatives.isEmpty()) {
            return toPage(caller, Page.empty(pageRequest));
        }
        Criteria criteria = Criteria.where("active").is(true).and("participantId").ne(caller.getSubject())
                .orOperator(alternatives);
        return toPage(caller, search(criteria, pageRequest));
    }

    /** Iscritti di un evento in ordine di arrivo, per chi lo gestisce (FR-023). */
    public EnrollmentPageDTO listForEvent(CallerContext caller, String eventId, EnrollmentStatus status,
                                          SeatPosition position, Integer page, Integer size) {
        Event event = eventRepositoryFind(eventId);
        if (event == null) {
            throw new BuonaCacciaException(ErrorCode.NOT_FOUND, "Evento non trovato.");
        }
        eventAccessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        PageRequest pageRequest = pageRequest(page, size, Sort.by(Sort.Direction.ASC, "arrivalOrder"));
        Criteria criteria = Criteria.where("eventId").is(eventId);
        if (status != null) {
            criteria.and("status").is(status);
        }
        if (position != null) {
            criteria.and("position").is(position);
        }
        return toPage(caller, search(criteria, pageRequest));
    }

    // ---------- adempimenti ----------

    /** Il capo unità o il capo gruppo del partecipante autorizza (FR-009). */
    public EnrollmentDTO approve(CallerContext caller, String id, ObligationType type) {
        Enrollment enrollment = find(id);
        User callerUser = authorizer(caller, enrollment, type);
        Instant now = clock.instant();
        EnrollmentObligation obligation = todoObligation(enrollment, type);
        obligation.setStatus(ObligationStatus.SATISFIED);
        obligation.setSatisfiedBy(caller.getSubject());
        obligation.setSatisfiedAt(now);
        completeIfDone(enrollment, now);
        Enrollment saved = save(enrollment);
        log.info("Iscrizione {}: {} autorizzata da {}", saved.getNumber(), type, caller.getSubject());
        return toDto(caller, callerUser, saved, eventRepositoryFind(saved.getEventId()), now);
    }

    /** Diniego motivato: l'iscrizione diventa RIFIUTATA e libera il posto (FR-009, FR-013). */
    public EnrollmentDTO reject(CallerContext caller, String id, ObligationType type, String reason) {
        Enrollment enrollment = find(id);
        User callerUser = authorizer(caller, enrollment, type);
        Instant now = clock.instant();
        EnrollmentObligation obligation = todoObligation(enrollment, type);
        obligation.setStatus(ObligationStatus.REJECTED);
        obligation.setSatisfiedBy(caller.getSubject());
        obligation.setSatisfiedAt(now);
        obligation.setNote(reason);
        Event event = eventRepositoryFind(enrollment.getEventId());
        Enrollment saved = exit(enrollment, event, ClosingReason.REJECTED, reason, null, now);
        log.info("Iscrizione {}: {} negata da {}", saved.getNumber(), type, caller.getSubject());
        return toDto(caller, callerUser, saved, event, now);
    }

    /** La segreteria che gestisce l'evento segna un adempimento come soddisfatto (FR-010). */
    public EnrollmentDTO satisfy(CallerContext caller, String id, ObligationType type, String note) {
        Enrollment enrollment = find(id);
        eventAccessPolicy.checkCanManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
        Instant now = clock.instant();
        EnrollmentObligation obligation = todoObligation(enrollment, type);
        obligation.setStatus(ObligationStatus.SATISFIED);
        obligation.setSatisfiedBy(caller.getSubject());
        obligation.setSatisfiedAt(now);
        obligation.setNote(note);
        completeIfDone(enrollment, now);
        Enrollment saved = save(enrollment);
        log.info("Iscrizione {}: {} soddisfatto dalla segreteria {}", saved.getNumber(), type, caller.getSubject());
        return toDto(caller, callerUser(caller), saved, eventRepositoryFind(saved.getEventId()), now);
    }

    // ---------- uscite ----------

    /** Ritiro del partecipante o del presentatore prima della partenza, con il diritto al rimborso (FR-018, FR-019). */
    public EnrollmentDTO withdraw(CallerContext caller, String id) {
        Enrollment enrollment = find(id);
        User callerUser = callerUser(caller);
        if (!accessPolicy.isParticipantOrPresenter(caller, enrollment)) {
            throw accessPolicy.canView(caller, callerUser, enrollment)
                    ? new BuonaCacciaException(ErrorCode.FORBIDDEN, "Si ritira solo il partecipante o il suo presentatore.")
                    : notFound();
        }
        if (!enrollment.isActive()) {
            throw stateConflict("L'iscrizione non è attiva.");
        }
        Event event = eventRepositoryFind(enrollment.getEventId());
        Instant now = clock.instant();
        if (event == null || !now.isBefore(event.getStartsAt())) {
            throw stateConflict("L'evento è già iniziato: non ci si può più ritirare.");
        }
        boolean refundDue = RefundRule.refundDue(event.getFee(), event.getStartDate(), enrollment.getPosition(), now);
        Enrollment saved = exit(enrollment, event, ClosingReason.WITHDRAWN, null, refundDue, now);
        log.info("Iscrizione {} ritirata da {} (rimborso: {})", saved.getNumber(), caller.getSubject(), refundDue);
        return toDto(caller, callerUser, saved, event, now);
    }

    /** Scadenza (FR-012): le iscrizioni REGISTRATE oltre la scadenza diventano SCADUTE. */
    public int expireOverdue() {
        Instant now = clock.instant();
        List<Enrollment> overdue = mongo(() -> enrollmentRepository.findByStatusAndDeadlineBefore(EnrollmentStatus.REGISTERED, now));
        Map<String, Event> events = new HashMap<>();
        int expired = 0;
        for (Enrollment enrollment : overdue) {
            Event event = events.computeIfAbsent(enrollment.getEventId(), this::eventRepositoryFind);
            if (tryExit(enrollment, event, ClosingReason.EXPIRED, now)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Iscrizioni scadute: {}", expired);
        }
        return expired;
    }

    /** Alla partenza chi è ancora in attesa è ANNULLATO con motivo "posti esauriti" (FR-016). */
    public int cancelWaitingAtStart() {
        Instant now = clock.instant();
        Query waiting = new Query(Criteria.where("active").is(true).and("position").is(SeatPosition.WAITING));
        List<String> eventIds = mongo(() -> mongoTemplate.findDistinct(waiting, "eventId", Enrollment.class, String.class));
        int cancelled = 0;
        for (Event event : mongo(() -> eventRepository.findAllById(eventIds))) {
            if (event.getStartsAt() == null || now.isBefore(event.getStartsAt())) {
                continue;
            }
            Query ofEvent = Query.of(waiting).addCriteria(Criteria.where("eventId").is(event.getId()));
            for (Enrollment enrollment : mongo(() -> mongoTemplate.find(ofEvent, Enrollment.class))) {
                if (tryExit(enrollment, event, ClosingReason.SEATS_EXHAUSTED, now)) {
                    cancelled++;
                }
            }
        }
        if (cancelled > 0) {
            log.info("Iscrizioni in attesa annullate alla partenza: {}", cancelled);
        }
        return cancelled;
    }

    /** Evento annullato: tutte le iscrizioni attive diventano ANNULLATE (FR-020). */
    public void cancelForEvent(Event event) {
        Instant now = clock.instant();
        boolean hasFee = event.getFee() != null && event.getFee().signum() > 0;
        long inSeats = cancelAll(event, SeatPosition.IN_SEATS, hasFee, now);
        long waiting = cancelAll(event, SeatPosition.WAITING, false, now);
        capacityService.reset(event.getId());
        log.info("Evento {} annullato: {} iscrizioni nei posti e {} in attesa annullate", event.getCode(), inSeats, waiting);
    }

    /**
     * Promuove i primi in attesa finché ci sono posti liberi (FR-013, FR-014). Il posto si prende
     * prima sulla capienza, poi si sposta la prima iscrizione in attesa con un solo aggiornamento
     * ordinato per arrivo: due promozioni in parallelo non scelgono mai la stessa iscrizione.
     */
    public void promoteWaiting(Event event) {
        Instant now = clock.instant();
        while (capacityService.takeFreeSeat(event.getId())) {
            Query first = new Query(Criteria.where("eventId").is(event.getId()).and("active").is(true)
                    .and("position").is(SeatPosition.WAITING)).with(Sort.by("arrivalOrder")).limit(1);
            Update update = new Update().set("position", SeatPosition.IN_SEATS).set("promotedAt", now).inc("version", 1);
            Enrollment next = mongo(() -> mongoTemplate.findAndModify(first, update,
                    FindAndModifyOptions.options().returnNew(true), Enrollment.class));
            if (next == null) {
                capacityService.giveBackFreeSeat(event.getId());
                return;
            }
            ObligationPlanner.onPromotion(next, event, now);
            next.setUpdatedAt(now);
            Enrollment saved = save(next);
            log.info("Iscrizione {} promossa nei posti ({})", saved.getNumber(), saved.getStatus());
        }
    }

    // ---------- aiuti ----------

    /** Chiude un'iscrizione attiva, libera la capienza e, se era nei posti, promuove (FR-013). */
    private Enrollment exit(Enrollment enrollment, Event event, ClosingReason reason, String note, Boolean refundDue,
                            Instant now) {
        if (!enrollment.isActive()) {
            throw stateConflict("L'iscrizione non è attiva.");
        }
        SeatPosition position = enrollment.getPosition();
        enrollment.setStatus(switch (reason) {
            case WITHDRAWN -> EnrollmentStatus.WITHDRAWN;
            case EXPIRED -> EnrollmentStatus.EXPIRED;
            case REJECTED -> EnrollmentStatus.REJECTED;
            case EVENT_CANCELLED, SEATS_EXHAUSTED -> EnrollmentStatus.CANCELLED;
        });
        enrollment.setActive(false);
        enrollment.setClosedAt(now);
        enrollment.setClosingReason(reason);
        enrollment.setClosingNote(note);
        enrollment.setRefundDue(refundDue != null ? refundDue : Boolean.FALSE);
        enrollment.setUpdatedAt(now);
        Enrollment saved = save(enrollment);
        capacityService.release(enrollment.getEventId(), position);
        if (position == SeatPosition.IN_SEATS && event != null) {
            promoteWaiting(event);
        }
        return saved;
    }

    /** Uscita dal job: se l'iscrizione è cambiata nel frattempo, ci riprova il giro successivo. */
    private boolean tryExit(Enrollment enrollment, Event event, ClosingReason reason, Instant now) {
        try {
            exit(enrollment, event, reason, null, null, now);
            return true;
        } catch (BuonaCacciaException e) {
            log.warn("Iscrizione {} non chiusa ({}): {}", enrollment.getNumber(), reason, e.getMessage());
            return false;
        }
    }

    private long cancelAll(Event event, SeatPosition position, boolean refundDue, Instant now) {
        Query query = new Query(Criteria.where("eventId").is(event.getId()).and("active").is(true).and("position").is(position));
        Update update = new Update().set("status", EnrollmentStatus.CANCELLED).set("active", false).set("closedAt", now)
                .set("closingReason", ClosingReason.EVENT_CANCELLED).set("closingNote", event.getCancellationReason())
                .set("refundDue", refundDue).set("updatedAt", now).inc("version", 1);
        return mongo(() -> mongoTemplate.updateMulti(query, update, Enrollment.class)).getModifiedCount();
    }

    private static void completeIfDone(Enrollment enrollment, Instant now) {
        if (enrollment.getStatus() == EnrollmentStatus.REGISTERED
                && ObligationPlanner.statusFor(enrollment.getObligations()) == EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(now);
        }
        enrollment.setUpdatedAt(now);
    }

    /** Chi chiama deve poter dare quell'autorizzazione; chi non vede l'iscrizione riceve 404. */
    private User authorizer(CallerContext caller, Enrollment enrollment, ObligationType type) {
        User callerUser = callerUser(caller);
        if (!accessPolicy.canAuthorize(callerUser, enrollment, type)) {
            throw accessPolicy.canView(caller, callerUser, enrollment)
                    ? new BuonaCacciaException(ErrorCode.FORBIDDEN, "Questa autorizzazione spetta a un altro capo.")
                    : notFound();
        }
        return callerUser;
    }

    private static EnrollmentObligation todoObligation(Enrollment enrollment, ObligationType type) {
        if (!enrollment.isActive()) {
            throw stateConflict("L'iscrizione non è attiva.");
        }
        return enrollment.getObligations().stream()
                .filter(o -> o.getType() == type && o.getStatus() == ObligationStatus.TODO)
                .findFirst()
                .orElseThrow(() -> stateConflict("L'iscrizione non ha l'adempimento " + type + " da fare."));
    }

    /** Il capo iscrive solo un ragazzo della propria unità (BR-02, AUTH-2): altrimenti 403. */
    private static void checkUnitLeaderOf(User leader, User participant) {
        boolean ok = leader != null && leader.getCensus() != null && participant.getCensus() != null
                && leader.getCensus().getMemberType() == MemberType.LEADER
                && participant.getCensus().getMemberType() == MemberType.YOUTH
                && leader.getCensus().getUnitId() != null
                && leader.getCensus().getUnitId().equals(participant.getCensus().getUnitId());
        if (!ok) {
            throw new BuonaCacciaException(ErrorCode.FORBIDDEN, "Puoi iscrivere solo i ragazzi della tua unità.");
        }
    }

    private Event publishedEvent(String eventId) {
        Event event = eventRepositoryFind(eventId);
        if (event == null || event.getLifecycle() == EventLifecycle.DRAFT) {
            throw new BuonaCacciaException(ErrorCode.NOT_FOUND, "Evento non trovato.");
        }
        return event;
    }

    private Event eventRepositoryFind(String eventId) {
        return mongo(() -> eventRepository.findById(eventId)).orElse(null);
    }

    private Enrollment find(String id) {
        return mongo(() -> enrollmentRepository.findById(id)).orElseThrow(EnrollmentService::notFound);
    }

    private Enrollment save(Enrollment enrollment) {
        return mongo(() -> enrollmentRepository.save(enrollment));
    }

    private User callerUser(CallerContext caller) {
        return caller.getSubject() == null ? null : mongo(() -> userRepository.findById(caller.getSubject())).orElse(null);
    }

    private Page<Enrollment> search(Criteria criteria, PageRequest pageRequest) {
        Query query = new Query(criteria);
        return mongo(() -> {
            long total = mongoTemplate.count(query, Enrollment.class);
            List<Enrollment> content = mongoTemplate.find(Query.of(query).with(pageRequest), Enrollment.class);
            return new PageImpl<>(content, pageRequest, total);
        });
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }

    private static Criteria todo(ObligationType type) {
        return Criteria.where("type").is(type).and("status").is(ObligationStatus.TODO);
    }

    private static PageRequest pageRequest(Integer page, Integer size, Sort sort) {
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
        return PageRequest.of(p, s, sort.and(Sort.by("id")));
    }

    private EnrollmentPageDTO toPage(CallerContext caller, Page<Enrollment> page) {
        User callerUser = callerUser(caller);
        Instant now = clock.instant();
        Collection<String> eventIds = page.getContent().stream().map(Enrollment::getEventId).collect(Collectors.toSet());
        Map<String, Event> events = eventIds.isEmpty() ? Map.of()
                : mongo(() -> eventRepository.findAllById(eventIds)).stream()
                        .collect(Collectors.toMap(Event::getId, Function.identity()));
        return EnrollmentPageDTO.builder()
                .content(page.getContent().stream()
                        .map(e -> toDto(caller, callerUser, e, events.get(e.getEventId()), now)).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private EnrollmentDTO toDto(CallerContext caller, User callerUser, Enrollment enrollment, Event event, Instant now) {
        EnrollmentDTO dto = EnrollmentMapper.INSTANCE.toDto(enrollment);
        dto.setObligations(enrollment.getObligations().stream()
                .map(o -> EnrollmentObligationDTO.builder().type(o.getType()).description(o.getDescription())
                        .status(o.getStatus()).satisfiedAt(o.getSatisfiedAt()).note(o.getNote()).build())
                .toList());
        if (event != null) {
            dto.setEventTitle(event.getTitle());
            dto.setEventStartDate(event.getStartDate());
        }
        if (enrollment.isActive() && enrollment.getPosition() == SeatPosition.WAITING) {
            Query ahead = new Query(Criteria.where("eventId").is(enrollment.getEventId()).and("active").is(true)
                    .and("position").is(SeatPosition.WAITING).and("arrivalOrder").lt(enrollment.getArrivalOrder()));
            dto.setWaitingPosition((int) (long) mongo(() -> mongoTemplate.count(ahead, Enrollment.class)) + 1);
        }
        dto.setAvailableActions(accessPolicy.availableActions(caller, callerUser, enrollment, event, now));
        return dto;
    }

    private static String fullName(User user) {
        return String.join(" ", Objects.toString(user.getFirstName(), ""), Objects.toString(user.getLastName(), "")).trim();
    }

    private static BuonaCacciaException stateConflict(String message) {
        return new BuonaCacciaException(ErrorCode.ENROLLMENT_STATE_CONFLICT, message);
    }

    private static BuonaCacciaException notFound() {
        return new BuonaCacciaException(ErrorCode.NOT_FOUND, "Iscrizione non trovata.");
    }
}
