package it.buonacaccia.service;

import java.time.Clock;
import java.util.function.Supplier;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.FormTemplate;
import it.buonacaccia.repository.FormTemplateRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.FormTemplateDTO;
import it.buonacaccia.v1.mapper.FormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Modelli di scheda per tipo evento (feature 007, FR-001). La scrittura è dell'amministrazione. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormTemplateService {

    private final FormTemplateRepository repository;
    private final EventTypeService eventTypeService;
    private final CircuitBreakerService circuitBreakerService;
    private final Clock clock;

    /** @throws BuonaCacciaException {@code NOT_FOUND} se il tipo non ha ancora un modello */
    public FormTemplate get(String typeCode) {
        return mongo(() -> repository.findById(typeCode))
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Il tipo " + typeCode + " non ha un modello di scheda."));
    }

    public boolean exists(String typeCode) {
        return typeCode != null && mongo(() -> repository.existsById(typeCode));
    }

    /**
     * @throws BuonaCacciaException {@code NOT_FOUND} per un tipo sconosciuto, {@code INVALID_INPUT} per un modello
     *                              non valido
     */
    public FormTemplate save(CallerContext caller, String typeCode, FormTemplateDTO request) {
        eventTypeService.get(typeCode);
        var sections = FormMapper.INSTANCE.toSections(request.getSections());
        var errors = FormRules.validateTemplate(sections);
        if (!errors.isEmpty()) {
            throw new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(), errors);
        }
        FormTemplate template = FormTemplate.builder().typeCode(typeCode).sections(sections)
                .updatedBy(caller.getSubject()).updatedAt(clock.instant()).build();
        log.info("Modello di scheda del tipo {} salvato da {}", typeCode, caller.getSubject());
        return mongo(() -> repository.save(template));
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }
}
