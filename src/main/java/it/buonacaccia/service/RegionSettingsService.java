package it.buonacaccia.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.Region;
import it.buonacaccia.model.RegionSettings;
import it.buonacaccia.repository.RegionSettingsRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.dto.RegionSettingsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinate di pagamento delle segreterie (FR-016…FR-018 della 005, research R10). Ogni segreteria
 * cura le proprie, l'amministrazione tutte e quelle nazionali ({@code NAT}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionSettingsService {

    private final RegionSettingsRepository repository;
    private final EventAccessPolicy accessPolicy;
    private final CircuitBreakerService circuitBreakerService;
    private final Clock clock;

    /** Impostazioni di una chiave; se non sono mai state salvate, vuote. */
    public RegionSettings get(String key) {
        String id = validKey(key);
        return find(id).orElseGet(() -> RegionSettings.builder().id(id).build());
    }

    public Optional<RegionSettings> find(String key) {
        return key == null ? Optional.empty() : mongo(() -> repository.findById(key));
    }

    /** Impostazioni che valgono per un evento: della sua regione, o nazionali per gli eventi nazionali. */
    public Optional<RegionSettings> forEvent(EventLevel level, Region region) {
        return find(level == EventLevel.NATIONAL || region == null ? RegionSettings.NATIONAL : region.name());
    }

    /**
     * @throws BuonaCacciaException {@code NOT_FOUND} per una sigla inesistente, {@code FORBIDDEN} se il
     *                              chiamante non cura quella chiave, {@code INVALID_INPUT} per un IBAN non valido
     */
    public RegionSettings save(CallerContext caller, String key, RegionSettingsDTO request) {
        String id = validKey(key);
        checkCanEdit(caller, id);
        String iban = request.getIban() == null || request.getIban().isBlank() ? null : Iban.normalize(request.getIban());
        if (iban != null && !Iban.isValid(iban)) {
            throw new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                    List.of(new FieldErrorDTO("iban", "L'IBAN non è valido.")));
        }
        RegionSettings settings = RegionSettings.builder()
                .id(id)
                .holder(blankToNull(request.getHolder()))
                .iban(iban)
                .contactEmail(blankToNull(request.getContactEmail()))
                .contactPhone(blankToNull(request.getContactPhone()))
                .hours(blankToNull(request.getHours()))
                .updatedBy(caller.getSubject())
                .updatedAt(clock.instant())
                .build();
        log.info("Coordinate di pagamento di {} aggiornate da {}", id, caller.getSubject());
        return mongo(() -> repository.save(settings));
    }

    private void checkCanEdit(CallerContext caller, String id) {
        if (RegionSettings.NATIONAL.equals(id)) {
            if (!caller.isAdmin()) {
                throw new BuonaCacciaException(ErrorCode.FORBIDDEN, "Le coordinate nazionali le cura l'amministrazione.");
            }
            return;
        }
        accessPolicy.checkCanManage(caller, EventLevel.REGIONAL, Region.valueOf(id));
    }

    private static String validKey(String key) {
        if (RegionSettings.NATIONAL.equals(key) || Region.fromCode(key).isPresent()) {
            return key;
        }
        throw new BuonaCacciaException(ErrorCode.NOT_FOUND, "Regione non trovata.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> T mongo(java.util.function.Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }
}
