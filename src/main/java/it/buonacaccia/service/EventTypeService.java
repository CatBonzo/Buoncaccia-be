package it.buonacaccia.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.EventCategory;
import it.buonacaccia.model.EventType;
import it.buonacaccia.repository.EventTypeRepository;
import it.buonacaccia.v1.dto.EventTypeDTO;
import it.buonacaccia.v1.mapper.EventTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Tipi evento (FR-001…FR-003): si creano, si modificano e si disattivano, non si cancellano. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventTypeService {

    private final EventTypeRepository eventTypeRepository;
    private final CircuitBreakerService circuitBreakerService;
    private final Clock clock;

    public List<EventType> findAll(boolean includeInactive) {
        return mongo(() -> includeInactive
                ? eventTypeRepository.findAllByOrderByCode()
                : eventTypeRepository.findByActiveTrueOrderByCode());
    }

    /** Tutti i tipi per codice, per completare gli eventi con nome e categoria. */
    public Map<String, EventType> byCode() {
        return findAll(true).stream().collect(Collectors.toMap(EventType::getCode, Function.identity()));
    }

    public Optional<EventType> find(String code) {
        return mongo(() -> eventTypeRepository.findById(code));
    }

    public List<String> codesOf(EventCategory category) {
        return mongo(() -> eventTypeRepository.findByCategory(category)).stream().map(EventType::getCode).toList();
    }

    /** @throws BuonaCacciaException {@code NOT_FOUND} se il tipo non esiste */
    public EventType get(String code) {
        return find(code).orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Tipo evento non trovato."));
    }

    /** @throws BuonaCacciaException {@code EVENT_TYPE_EXISTS} se il codice è già usato */
    public EventType create(EventTypeDTO dto) {
        if (find(dto.getCode()).isPresent()) {
            throw new BuonaCacciaException(ErrorCode.EVENT_TYPE_EXISTS);
        }
        EventType type = EventTypeMapper.INSTANCE.toEntity(dto);
        Instant now = clock.instant();
        type.setActive(true);
        type.setCreatedAt(now);
        type.setUpdatedAt(now);
        log.info("Tipo evento {} creato", type.getCode());
        return mongo(() -> eventTypeRepository.insert(type));
    }

    public EventType update(String code, EventTypeDTO dto) {
        EventType type = get(code);
        EventTypeMapper.INSTANCE.update(dto, type);
        type.setUpdatedAt(clock.instant());
        log.info("Tipo evento {} modificato", code);
        return mongo(() -> eventTypeRepository.save(type));
    }

    /** Il tipo non si potrà usare per nuovi eventi; quelli esistenti restano come sono (FR-003). */
    public EventType deactivate(String code) {
        EventType type = get(code);
        type.setActive(false);
        type.setUpdatedAt(clock.instant());
        log.info("Tipo evento {} disattivato", code);
        return mongo(() -> eventTypeRepository.save(type));
    }

    private <T> T mongo(java.util.function.Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }
}
