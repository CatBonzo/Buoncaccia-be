package it.buonacaccia.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.DomainEventType;
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
import it.buonacaccia.repository.NotificationRepository;
import it.buonacaccia.notification.DomainEventRecorder;
import it.buonacaccia.repository.UserRepository;
import it.buonacaccia.service.EventCapacityService.Reservation;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EnrollmentAccessPolicy;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.EnrollmentDTO;
import it.buonacaccia.v1.dto.PaymentInfoDTO;
import it.buonacaccia.v1.dto.PaymentSummaryDTO;
import it.buonacaccia.model.RegionSettings;
import it.buonacaccia.v1.dto.EnrollmentObligationDTO;
import it.buonacaccia.v1.dto.EnrollmentPageDTO;
import it.buonacaccia.v1.dto.EnrollmentRequestDTO;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.dto.NotificationDTO;
import it.buonacaccia.v1.mapper.EnrollmentMapper;
import it.buonacaccia.v1.mapper.NotificationMapper;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Iscrizioni e lista d'attesa (feature 003): iscrizione autonoma o del capo, pagina di stato,
 * autorizzazioni e adempimenti, ritiro, scadenze, promozioni, elenchi e annullamento con l'evento.
 * Posti e ordine di arrivo li assegna {@link EventCapacityService}; ogni uscita di un'iscrizione
 * nei posti promuove il primo in attesa (BR-08). Dalla feature 004 ogni cambio di stato si salva
 * insieme al suo evento di dominio, nella stessa transazione ({@link #save(Enrollment, Event, DomainEventType)}).
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
    private final DomainEventRecorder domainEvents;
    private final NotificationRepository notificationRepository;
    private final TransactionTemplate transactionTemplate;
    private final RegionSettingsService regionSettingsService;
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
                .feeCents(Money.feeToCents(event.getFee()))
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
            enrollment = mongo(() -> transactionTemplate.execute(tx -> {
                Enrollment inserted = enrollmentRepository.insert(toInsert);
                domainEvents.enrollment(DomainEventType.ENROLLMENT_REGISTERED, inserted, event);
                domainEvents.authorizationsRequested(inserted, event);
                return inserted;
            }));
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

    /** Registro degli avvisi dell'iscrizione, per chi gestisce l'evento (FR-007 della 004); per gli altri 404. */
    public List<NotificationDTO> notifications(CallerContext caller, String id) {
        Enrollment enrollment = find(id);
        if (!accessPolicy.canManage(caller, enrollment)) {
            throw notFound();
        }
        return NotificationMapper.INSTANCE.toDto(mongo(() -> notificationRepository.findByEnrollmentIdOrderByCreatedAtAsc(id)));
    }

    // ---------- adempimenti ----------

    /** Il capo unità o il capo gruppo del partecipante autorizza (FR-009). */
    public EnrollmentDTO approve(CallerContext caller, String id, ObligationType type) {
        Enrollment enrollment = find(id);
        User callerUser = authorizer(caller, enrollment, type);
        Instant now = clock.instant();
        Event event = eventRepositoryFind(enrollment.getEventId());
        Enrollment saved = approveBy(enrollment, event, callerUser, type, now);
        return toDto(caller, callerUser, saved, event, now);
    }

    /**
     * Autorizzazione dal link nell'e-mail (FR-019 della 004): stessi controlli e stessi effetti
     * dell'approvazione dal sito, a nome del capo a cui è stato inviato il link.
     */
    public Enrollment approveAsLeader(String id, ObligationType type, String leaderId) {
        Enrollment enrollment = find(id);
        User leader = leader(enrollment, type, leaderId);
        Instant now = clock.instant();
        return approveBy(enrollment, eventRepositoryFind(enrollment.getEventId()), leader, type, now);
    }

    /** Diniego dal link nell'e-mail (FR-019 della 004). */
    public Enrollment rejectAsLeader(String id, ObligationType type, String leaderId, String reason) {
        Enrollment enrollment = find(id);
        User leader = leader(enrollment, type, leaderId);
        Instant now = clock.instant();
        return rejectBy(enrollment, eventRepositoryFind(enrollment.getEventId()), leader, type, reason, now);
    }

    private Enrollment approveBy(Enrollment enrollment, Event event, User leader, ObligationType type, Instant now) {
        EnrollmentObligation obligation = todoObligation(enrollment, type);
        obligation.setStatus(ObligationStatus.SATISFIED);
        obligation.setSatisfiedBy(leader.getId());
        obligation.setSatisfiedAt(now);
        Enrollment saved = save(enrollment, event, completeIfDone(enrollment, now));
        log.info("Iscrizione {}: {} autorizzata da {}", saved.getNumber(), type, leader.getId());
        return saved;
    }

    private Enrollment rejectBy(Enrollment enrollment, Event event, User leader, ObligationType type, String reason,
                                Instant now) {
        EnrollmentObligation obligation = todoObligation(enrollment, type);
        obligation.setStatus(ObligationStatus.REJECTED);
        obligation.setSatisfiedBy(leader.getId());
        obligation.setSatisfiedAt(now);
        obligation.setNote(reason);
        Enrollment saved = exit(enrollment, event, ClosingReason.REJECTED, reason, null, now);
        log.info("Iscrizione {}: {} negata da {}", saved.getNumber(), type, leader.getId());
        return saved;
    }

    /** Diniego motivato: l'iscrizione diventa RIFIUTATA e libera il posto (FR-009, FR-013). */
    public EnrollmentDTO reject(CallerContext caller, String id, ObligationType type, String reason) {
        Enrollment enrollment = find(id);
        User callerUser = authorizer(caller, enrollment, type);
        Instant now = clock.instant();
        Event event = eventRepositoryFind(enrollment.getEventId());
        Enrollment saved = rejectBy(enrollment, event, callerUser, type, reason, now);
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
        Event event = eventRepositoryFind(enrollment.getEventId());
        Enrollment saved = save(enrollment, event, completeIfDone(enrollment, now));
        log.info("Iscrizione {}: {} soddisfatto dalla segreteria {}", saved.getNumber(), type, caller.getSubject());
        return toDto(caller, callerUser(caller), saved, event, now);
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

    /**
     * Promemoria a 3 e a 1 giorno di calendario dalla scadenza, all'ora italiana (FR-010 della 004,
     * research R9). Ogni promemoria si segna nell'iscrizione con la sua scadenza, nella stessa
     * transazione dell'evento di dominio: non parte due volte, e dopo una promozione con una nuova
     * scadenza i promemoria ripartono. Se il giorno prima è già arrivato, il promemoria a 3 giorni
     * non parte più.
     */
    public int sendReminders() {
        Instant now = clock.instant();
        Query due = new Query(Criteria.where("status").is(EnrollmentStatus.REGISTERED)
                .and("deadline").gt(now).lte(now.plus(Duration.ofDays(4))));
        Map<String, Event> events = new HashMap<>();
        int sent = 0;
        for (Enrollment enrollment : mongo(() -> mongoTemplate.find(due, Enrollment.class))) {
            int days = reminderDue(enrollment.getDeadline(), now, enrollment.getRemindersSent());
            if (days == 0) {
                continue;
            }
            Event event = events.computeIfAbsent(enrollment.getEventId(), this::eventRepositoryFind);
            String key = reminderKey(days, enrollment.getDeadline());
            Update update = new Update().addToSet("remindersSent", key).inc("version", 1);
            if (days == 1) {
                update = new Update().addToSet("remindersSent").each(key, reminderKey(3, enrollment.getDeadline()))
                        .inc("version", 1);
            }
            Query notYet = new Query(Criteria.where("id").is(enrollment.getId())
                    .and("status").is(EnrollmentStatus.REGISTERED).and("remindersSent").ne(key));
            Update mark = update;
            boolean recorded = Boolean.TRUE.equals(mongo(() -> transactionTemplate.execute(tx -> {
                if (mongoTemplate.updateFirst(notYet, mark, Enrollment.class).getModifiedCount() == 0) {
                    return false;
                }
                domainEvents.reminder(enrollment, event, days);
                return true;
            })));
            if (recorded) {
                sent++;
                log.debug("Promemoria a {} giorni per l'iscrizione {}", days, enrollment.getNumber());
            }
        }
        if (sent > 0) {
            log.info("Promemoria di scadenza: {}", sent);
        }
        return sent;
    }

    /** 1 o 3 se è il momento di quel promemoria e non è ancora partito, altrimenti 0. */
    static int reminderDue(Instant deadline, Instant now, Set<String> sent) {
        ZonedDateTime local = deadline.atZone(ClockConfig.ROME);
        if (!now.isBefore(local.minusDays(1).toInstant())) {
            return sent.contains(reminderKey(1, deadline)) ? 0 : 1;
        }
        if (!now.isBefore(local.minusDays(3).toInstant())) {
            return sent.contains(reminderKey(3, deadline)) ? 0 : 3;
        }
        return 0;
    }

    private static String reminderKey(int days, Instant deadline) {
        return "D" + days + ":" + deadline;
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
            Enrollment saved = save(next, event, DomainEventType.ENROLLMENT_PROMOTED);
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
        Enrollment saved = save(enrollment, event, switch (reason) {
            case WITHDRAWN -> DomainEventType.ENROLLMENT_WITHDRAWN;
            case EXPIRED -> DomainEventType.ENROLLMENT_EXPIRED;
            case REJECTED -> DomainEventType.ENROLLMENT_REJECTED;
            case EVENT_CANCELLED, SEATS_EXHAUSTED -> DomainEventType.ENROLLMENT_CANCELLED;
        });
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

    /** Annulla insieme le iscrizioni attive di una posizione e scrive un evento di dominio per ciascuna (FR-013 della 004). */
    private long cancelAll(Event event, SeatPosition position, boolean refundDue, Instant now) {
        Query query = new Query(Criteria.where("eventId").is(event.getId()).and("active").is(true).and("position").is(position));
        Update update = new Update().set("status", EnrollmentStatus.CANCELLED).set("active", false).set("closedAt", now)
                .set("closingReason", ClosingReason.EVENT_CANCELLED).set("closingNote", event.getCancellationReason())
                .set("refundDue", refundDue).set("updatedAt", now).inc("version", 1);
        return mongo(() -> transactionTemplate.execute(tx -> {
            List<Enrollment> active = mongoTemplate.find(query, Enrollment.class);
            long modified = mongoTemplate.updateMulti(query, update, Enrollment.class).getModifiedCount();
            for (Enrollment enrollment : active) {
                enrollment.setStatus(EnrollmentStatus.CANCELLED);
                enrollment.setActive(false);
                enrollment.setClosingReason(ClosingReason.EVENT_CANCELLED);
                enrollment.setClosingNote(event.getCancellationReason());
                enrollment.setRefundDue(refundDue);
                domainEvents.enrollment(DomainEventType.ENROLLMENT_CANCELLED, enrollment, event);
            }
            return modified;
        }));
    }

    /** @return {@code ENROLLMENT_COMPLETED} se l'iscrizione è appena diventata completa, altrimenti null */
    static DomainEventType completeIfDone(Enrollment enrollment, Instant now) {
        enrollment.setUpdatedAt(now);
        if (enrollment.getStatus() == EnrollmentStatus.REGISTERED
                && ObligationPlanner.statusFor(enrollment.getObligations()) == EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(now);
            return DomainEventType.ENROLLMENT_COMPLETED;
        }
        return null;
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

    /** Il capo del link: deve poter ancora dare quell'autorizzazione (es. non ha cambiato unità). */
    private User leader(Enrollment enrollment, ObligationType type, String leaderId) {
        User leader = mongo(() -> userRepository.findById(leaderId)).orElse(null);
        if (!accessPolicy.canAuthorize(leader, enrollment, type)) {
            throw stateConflict("Questa autorizzazione non spetta più a questo capo.");
        }
        return leader;
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

    /** Salva l'iscrizione e, se {@code type} non è null, il suo evento di dominio nella stessa transazione (R2 della 004). */
    private Enrollment save(Enrollment enrollment, Event event, DomainEventType type) {
        return mongo(() -> transactionTemplate.execute(tx -> {
            Enrollment saved = enrollmentRepository.save(enrollment);
            if (type != null && event != null) {
                domainEvents.enrollment(type, saved, event);
            }
            return saved;
        }));
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
        setPayment(dto, enrollment, event);
        return dto;
    }

    /** Quota, versato e residuo (R9 della 005); le coordinate solo se il pagamento è ancora da fare. */
    private void setPayment(EnrollmentDTO dto, Enrollment enrollment, Event event) {
        long fee = enrollment.getFeeCents() != null ? enrollment.getFeeCents()
                : event == null ? 0 : Money.feeToCents(event.getFee());
        if (fee <= 0) {
            return;
        }
        dto.setPayment(PaymentSummaryDTO.builder()
                .fee(Money.toEuro(fee))
                .paid(Money.toEuro(enrollment.getPaidCents()))
                .due(Money.toEuro(Math.max(0, fee - enrollment.getPaidCents())))
                .build());
        boolean toPay = enrollment.isActive() && enrollment.getObligations().stream()
                .anyMatch(o -> o.getType() == ObligationType.PAYMENT && o.getStatus() == ObligationStatus.TODO);
        if (toPay) {
            RegionSettings settings = regionSettingsService.forEvent(enrollment.getEventLevel(), enrollment.getEventRegion())
                    .orElseGet(RegionSettings::new);
            dto.setPaymentInfo(PaymentInfoDTO.builder().holder(settings.getHolder()).iban(settings.getIban())
                    .contactEmail(settings.getContactEmail()).contactPhone(settings.getContactPhone())
                    .hours(settings.getHours()).build());
        }
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
