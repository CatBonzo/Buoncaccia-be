package it.buonacaccia.v1.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.service.EventTypeService;
import it.buonacaccia.service.guard.SecurityGuardService;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.EventTypeDTO;
import it.buonacaccia.v1.mapper.EventTypeMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/event-types")
@Tag(name = "event-types", description = "Tipi evento (gestione riservata all'amministrazione)")
public class EventTypeController {

    private final EventTypeService eventTypeService;

    @GetMapping
    @Operation(summary = "Tipi evento attivi; con includeInactive=true anche i disattivati (entitlement tipoevento:gestione)")
    public ApiResponse<List<EventTypeDTO>> findAll(@RequestParam(defaultValue = "false") boolean includeInactive,
                                                   @AuthenticationPrincipal Jwt jwt) {
        if (includeInactive) {
            if (jwt == null) {
                throw new BuonaCacciaException(ErrorCode.UNAUTHENTICATED);
            }
            List<String> entitlements = jwt.getClaimAsStringList(SecurityGuardService.ENTITLEMENT_CLAIM);
            if (entitlements == null || !entitlements.contains(Entitlements.TIPOEVENTO_GESTIONE)) {
                throw new BuonaCacciaException(ErrorCode.FORBIDDEN);
            }
        }
        return ApiResponse.ok(EventTypeMapper.INSTANCE.toDtoList(eventTypeService.findAll(includeInactive)));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Un tipo evento")
    public ApiResponse<EventTypeDTO> get(@PathVariable String code) {
        return ApiResponse.ok(EventTypeMapper.INSTANCE.toDto(eventTypeService.get(code)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityAccess(entitlement = Entitlements.TIPOEVENTO_GESTIONE)
    @Operation(summary = "Crea un tipo evento (FR-001)")
    public ApiResponse<EventTypeDTO> create(@Valid @RequestBody EventTypeDTO request) {
        log.debug("POST /event-types {}", request.getCode());
        return ApiResponse.ok(EventTypeMapper.INSTANCE.toDto(eventTypeService.create(request)));
    }

    @PutMapping("/{code}")
    @SecurityAccess(entitlement = Entitlements.TIPOEVENTO_GESTIONE)
    @Operation(summary = "Modifica nome, categoria, default e candidatura (il codice non cambia)")
    public ApiResponse<EventTypeDTO> update(@PathVariable String code, @Valid @RequestBody EventTypeDTO request) {
        return ApiResponse.ok(EventTypeMapper.INSTANCE.toDto(eventTypeService.update(code, request)));
    }

    @PostMapping("/{code}/deactivate")
    @SecurityAccess(entitlement = Entitlements.TIPOEVENTO_GESTIONE)
    @Operation(summary = "Disattiva un tipo evento: non si potrà usare per nuovi eventi (FR-003)")
    public ApiResponse<EventTypeDTO> deactivate(@PathVariable String code) {
        return ApiResponse.ok(EventTypeMapper.INSTANCE.toDto(eventTypeService.deactivate(code)));
    }
}
