package it.buonacaccia.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.Availability;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventCapacity;
import it.buonacaccia.model.EventCategory;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.EventLifecycle;
import it.buonacaccia.model.EventStatus;
import it.buonacaccia.model.EventType;
import it.buonacaccia.model.Region;
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.repository.EventSearchRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.CancelDTO;
import it.buonacaccia.v1.dto.EventDTO;
import it.buonacaccia.v1.dto.EventPageDTO;
import it.buonacaccia.v1.dto.EventRequestDTO;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Eventi: creazione, modifica, pubblicazione, chiusura anticipata, annullamento, ricerca e
 * dettaglio (US1–US5). Ogni scrittura passa prima dalla regola territoriale (FR-010); lo stato
 * effettivo si calcola all'ora del {@link Clock} (research R1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    /** Ordinamenti ammessi e campo su cui si ordina (FR-019). */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "startDate", "startsAt",
            "title", "title",
            "type", "typeCode");
    private static final Set<EventStatus> CANCELLABLE = Set.of(EventStatus.DRAFT, EventStatus.PUBLISHED,
            EventStatus.REGISTRATION_OPEN, EventStatus.REGISTRATION_CLOSED, EventStatus.IN_PROGRESS);
    private static final Set<EventStatus> EDITABLE_AFTER_PUBLISH = Set.of(EventStatus.PUBLISHED,
            EventStatus.REGISTRATION_OPEN, EventStatus.REGISTRATION_CLOSED);
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_QUERY_LENGTH = 100;

    private final EventRepository eventRepository;
    private final EventSearchRepository eventSearchRepository;
    private final EventTypeService eventTypeService;
    private final EventCodeGenerator eventCodeGenerator;
    private final EventStatusResolver statusResolver;
    private final EventValidator validator;
    private final EventAccessPolicy accessPolicy;
    private final EventCapacityService capacityService;
    private final EnrollmentService enrollmentService;
    private final CircuitBreakerService circuitBreakerService;
    private final Clock clock;

    // ---------- gestione ----------

    /** Crea una bozza (FR-004, FR-005, FR-008). Per la segreteria la regione mancante è la sua. */
    public EventDTO create(CallerContext caller, EventRequestDTO request) {
        Event event = EventMapper.INSTANCE.toEntity(request);
        normalizeRegion(caller, event);
        accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        EventType type = activeType(event.getTypeCode());
        applyTypeDefaults(event, type);

        Instant now = clock.instant();
        event.setId(UUID.randomUUID().toString());
        event.setCode(eventCodeGenerator.next(event.getRegion(), yearOf(event)));
        event.setLifecycle(EventLifecycle.DRAFT);
        event.setCreatedBy(caller.getSubject());
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        statusResolver.computeInstants(event);
        log.info("Evento {} creato in bozza da {}", event.getCode(), caller.getSubject());
        return toDto(save(event));
    }

    /** Modifica: in bozza tutti i campi; dopo la pubblicazione solo quelli consentiti (FR-016…FR-018). */
    public EventDTO update(CallerContext caller, String id, EventRequestDTO request) {
        Event event = get(id);
        accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        checkVersion(event, request.getVersion(), true);
        Instant now = clock.instant();
        EventStatus status = statusResolver.status(event, now);
        Integer previousSeats = null;

        if (status == EventStatus.DRAFT) {
            String previousType = event.getTypeCode();
            EventMapper.INSTANCE.update(request, event);
            normalizeRegion(caller, event);
            accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
            EventType type = event.getTypeCode().equals(previousType)
                    ? eventTypeService.find(event.getTypeCode()).orElse(null)
                    : activeType(event.getTypeCode());
            applyTypeDefaults(event, type);
        } else if (EDITABLE_AFTER_PUBLISH.contains(status)) {
            previousSeats = event.getSeats();
            validator.checkLockedFields(event, request);
            if (request.getRegistrationCloseDate() != null
                    && !request.getRegistrationCloseDate().equals(event.getRegistrationCloseDate())
                    && now.isAfter(event.getRegistrationClosesAt())) {
                throw new BuonaCacciaException(ErrorCode.FIELD_LOCKED,
                        "Il campo registrationCloseDate non è modificabile: la chiusura è già passata.");
            }
            applyEditableFields(event, request);
            validator.checkInvariants(event, eventTypeService.find(event.getTypeCode()).isPresent());
        } else {
            throw stateConflict("Un evento " + status + " non si può modificare.");
        }
        event.setUpdatedAt(now);
        statusResolver.computeInstants(event);
        Event saved = previousSeats == null || previousSeats.equals(event.getSeats())
                ? save(event) : saveWithNewSeats(event, previousSeats);
        log.info("Evento {} modificato da {}", event.getCode(), caller.getSubject());
        return toDto(saved);
    }

    /** Pubblica una bozza se rispetta tutti gli invarianti (FR-007). */
    public EventDTO publish(CallerContext caller, String id, Long version) {
        Event event = get(id);
        accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        checkVersion(event, version, false);
        if (event.getLifecycle() != EventLifecycle.DRAFT) {
            throw stateConflict("Si pubblica solo un evento in bozza.");
        }
        validator.checkInvariants(event, eventTypeService.find(event.getTypeCode()).isPresent());
        event.setLifecycle(EventLifecycle.PUBLISHED);
        event.setUpdatedAt(clock.instant());
        statusResolver.computeInstants(event);
        Event saved = save(event);
        capacityService.ensure(saved.getId(), saved.getSeats());
        log.info("Evento {} pubblicato da {}", event.getCode(), caller.getSubject());
        return toDto(saved);
    }

    /** Chiusura anticipata delle iscrizioni, solo se sono aperte (FR-015). */
    public EventDTO closeRegistrations(CallerContext caller, String id, Long version) {
        Event event = get(id);
        accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        checkVersion(event, version, false);
        Instant now = clock.instant();
        if (statusResolver.status(event, now) != EventStatus.REGISTRATION_OPEN) {
            throw stateConflict("Si chiudono in anticipo solo iscrizioni aperte.");
        }
        event.setRegistrationClosedEarlyAt(now);
        event.setUpdatedAt(now);
        log.info("Iscrizioni dell'evento {} chiuse in anticipo da {}", event.getCode(), caller.getSubject());
        return toDto(save(event));
    }

    /** Annullamento motivato, da bozza fino a in corso (FR-017). */
    public EventDTO cancel(CallerContext caller, String id, CancelDTO request) {
        Event event = get(id);
        accessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        checkVersion(event, request.getVersion(), true);
        Instant now = clock.instant();
        EventStatus status = statusResolver.status(event, now);
        if (!CANCELLABLE.contains(status)) {
            throw stateConflict("Un evento " + status + " non si può annullare.");
        }
        event.setLifecycle(EventLifecycle.CANCELLED);
        event.setCancellationReason(request.getReason());
        event.setCancelledAt(now);
        event.setUpdatedAt(now);
        Event saved = save(event);
        enrollmentService.cancelForEvent(saved);
        log.info("Evento {} annullato da {}: {}", event.getCode(), caller.getSubject(), request.getReason());
        return toDto(saved);
    }

    /** Eventi gestibili dal chiamante in tutti gli stati, bozze comprese (FR-009). */
    public EventPageDTO findManaged(CallerContext caller, EventStatus status, Integer page, Integer size) {
        accessPolicy.checkManagesSomething(caller);
        List<FieldErrorDTO> errors = new ArrayList<>();
        PageRequest pageRequest = pageRequest(page, size, "startDate,asc", errors);
        throwIfInvalid(errors);

        Instant now = clock.instant();
        List<Criteria> conditions = new ArrayList<>();
        if (!caller.isAdmin()) {
            Region region = caller.region().orElseThrow();
            conditions.add(Criteria.where("level").in(EventLevel.REGIONAL, EventLevel.ZONE).and("region").is(region));
        }
        if (status != null) {
            conditions.add(EventSearchRepository.status(status, now));
        }
        return toPage(search(conditions, pageRequest), now);
    }

    // ---------- consultazione pubblica ----------

    /** Ricerca pubblica (FR-019): pubblicati, e annullati solo con il filtro esplicito (FR-017). */
    public EventPageDTO search(Region region, EventCategory category, String type, Availability availability,
                               EventStatus status, LocalDate startFrom, LocalDate startTo, String q,
                               Integer page, Integer size, String sort) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        if (status == EventStatus.DRAFT) {
            errors.add(new FieldErrorDTO("status", "Le bozze non compaiono nel catalogo pubblico."));
        }
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            errors.add(new FieldErrorDTO("q", "Al massimo " + MAX_QUERY_LENGTH + " caratteri."));
        }
        PageRequest pageRequest = pageRequest(page, size, sort, errors);
        throwIfInvalid(errors);

        Instant now = clock.instant();
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(status == null
                ? Criteria.where("lifecycle").is(EventLifecycle.PUBLISHED)
                : EventSearchRepository.status(status, now));
        if (region != null) {
            conditions.add(Criteria.where("region").is(region));
        }
        if (category != null) {
            conditions.add(Criteria.where("typeCode").in(eventTypeService.codesOf(category)));
        }
        if (type != null && !type.isBlank()) {
            conditions.add(Criteria.where("typeCode").is(type));
        }
        if (availability != null) {
            conditions.add(EventSearchRepository.availability(availability, now,
                    capacityService.idsWithOccupancyAtLeast(EventStatusResolver.LIMITED_FROM_PERCENT),
                    capacityService.idsWithOccupancyAbove(EventStatusResolver.HEAVY_OVERBOOKING_ABOVE_PERCENT)));
        }
        if (startFrom != null) {
            conditions.add(Criteria.where("startsAt").gte(startFrom.atStartOfDay(ClockConfig.ROME).toInstant()));
        }
        if (startTo != null) {
            conditions.add(Criteria.where("startsAt").lt(startTo.plusDays(1).atStartOfDay(ClockConfig.ROME).toInstant()));
        }
        if (q != null && !q.isBlank()) {
            conditions.add(Criteria.where("title").regex(Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE)));
        }
        return toPage(search(conditions, pageRequest), now);
    }

    /** Dettaglio (FR-020): una bozza esiste solo per chi la può gestire. */
    public EventDTO getDetail(CallerContext caller, String id) {
        Event event = get(id);
        if (event.getLifecycle() == EventLifecycle.DRAFT
                && !accessPolicy.canManage(caller, event.getLevel(), event.getRegion())) {
            throw notFound();
        }
        return toDto(event);
    }

    // ---------- aiuti ----------

    private Event get(String id) {
        return mongo(() -> eventRepository.findById(id)).orElseThrow(EventService::notFound);
    }

    private Event save(Event event) {
        return mongo(() -> eventRepository.save(event));
    }

    /**
     * Cambio dei posti di un evento pubblicato (FR-014 della 003, research R10): prima la capienza,
     * che rifiuta posti sotto gli iscritti nei posti; se poi il salvataggio fallisce la si riporta
     * indietro. Con più posti si promuovono i primi in attesa.
     */
    private Event saveWithNewSeats(Event event, int previousSeats) {
        capacityService.updateSeats(event.getId(), event.getSeats());
        Event saved;
        try {
            saved = save(event);
        } catch (BuonaCacciaException e) {
            capacityService.updateSeats(event.getId(), previousSeats);
            throw e;
        }
        if (saved.getSeats() > previousSeats) {
            enrollmentService.promoteWaiting(saved);
        }
        return saved;
    }

    private Page<Event> search(List<Criteria> conditions, PageRequest pageRequest) {
        return mongo(() -> eventSearchRepository.search(conditions, pageRequest));
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }

    private EventType activeType(String code) {
        return eventTypeService.find(code)
                .filter(EventType::isActive)
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                        List.of(new FieldErrorDTO("typeCode", "Tipo evento inesistente o disattivato."))));
    }

    /** Requisiti e adempimenti predefiniti del tipo, se la richiesta non li indica (FR-005). */
    private static void applyTypeDefaults(Event event, EventType type) {
        if (type == null) {
            return;
        }
        if (event.getRequirements() == null && type.getDefaultRequirements() != null) {
            event.setRequirements(type.getDefaultRequirements());
        }
        if (event.getObligations() == null || event.getObligations().isEmpty()) {
            event.setObligations(new ArrayList<>(type.getDefaultObligations()));
        }
    }

    private static void normalizeRegion(CallerContext caller, Event event) {
        if (event.getLevel() == EventLevel.NATIONAL) {
            event.setRegion(null);
        } else if (event.getRegion() == null && !caller.isAdmin()) {
            event.setRegion(caller.region().orElse(null));
        }
    }

    private static void applyEditableFields(Event event, EventRequestDTO request) {
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setProvince(request.getProvince());
        event.setContacts(EventMapper.INSTANCE.toContacts(request.getContacts()));
        event.setSeats(request.getSeats());
        event.setFee(request.getFee());
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());
        if (request.getRegistrationCloseDate() != null) {
            event.setRegistrationCloseDate(request.getRegistrationCloseDate());
        }
    }

    private int yearOf(Event event) {
        return event.getStartDate() != null ? event.getStartDate().getYear()
                : clock.instant().atZone(ClockConfig.ROME).getYear();
    }

    private static void checkVersion(Event event, Long version, boolean required) {
        if (version == null) {
            if (required) {
                throw new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                        List.of(new FieldErrorDTO("version", "Indica la versione letta dell'evento.")));
            }
            return;
        }
        if (!version.equals(event.getVersion())) {
            throw new BuonaCacciaException(ErrorCode.STALE_VERSION);
        }
    }

    private static PageRequest pageRequest(Integer page, Integer size, String sort, List<FieldErrorDTO> errors) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        if (p < 0) {
            errors.add(new FieldErrorDTO("page", "La pagina parte da 0."));
        }
        if (s < 1 || s > MAX_PAGE_SIZE) {
            errors.add(new FieldErrorDTO("size", "Da 1 a " + MAX_PAGE_SIZE + " risultati per pagina."));
        }
        String[] parts = (sort == null || sort.isBlank() ? "startDate,asc" : sort).split(",");
        String field = SORT_FIELDS.get(parts[0]);
        String direction = parts.length > 1 ? parts[1] : "asc";
        if (field == null || parts.length > 2 || !Set.of("asc", "desc").contains(direction)) {
            errors.add(new FieldErrorDTO("sort", "Ordinamento: startDate, title o type, seguito da ,asc o ,desc."));
            return null;
        }
        if (p < 0 || s < 1 || s > MAX_PAGE_SIZE) {
            return null;
        }
        Sort order = Sort.by(Sort.Direction.fromString(direction), field).and(Sort.by("code"));
        return PageRequest.of(p, s, order);
    }

    private static void throwIfInvalid(List<FieldErrorDTO> errors) {
        if (!errors.isEmpty()) {
            throw new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(), errors);
        }
    }

    private EventPageDTO toPage(Page<Event> page, Instant now) {
        Map<String, EventType> types = eventTypeService.byCode();
        Map<String, Integer> enrolled = capacityService.activeCounts(page.getContent().stream().map(Event::getId).toList());
        return EventPageDTO.builder()
                .content(page.getContent().stream()
                        .map(event -> toDto(event, now, types, enrolled.getOrDefault(event.getId(), 0))).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private EventDTO toDto(Event event) {
        int enrolled = capacityService.find(event.getId()).map(EventCapacity::getActive).orElse(0);
        return toDto(event, clock.instant(), eventTypeService.byCode(), enrolled);
    }

    /** {@code enrolled}: iscrizioni attive lette dalla capienza (research R2 della 003). */
    private EventDTO toDto(Event event, Instant now, Map<String, EventType> types, int enrolled) {
        EventDTO dto = EventMapper.INSTANCE.toDto(event);
        dto.setStatus(statusResolver.status(event, now));
        dto.setEnrolledCount(enrolled);
        dto.setAvailability(event.getLifecycle() == EventLifecycle.DRAFT ? Availability.CLOSED
                : statusResolver.availability(event, enrolled, now));
        EventType type = types.get(event.getTypeCode());
        if (type != null) {
            dto.setTypeName(type.getName());
            dto.setCategory(type.getCategory());
        }
        return dto;
    }

    private static BuonaCacciaException stateConflict(String message) {
        return new BuonaCacciaException(ErrorCode.STATE_CONFLICT, message);
    }

    private static BuonaCacciaException notFound() {
        return new BuonaCacciaException(ErrorCode.NOT_FOUND, "Evento non trovato.");
    }
}
