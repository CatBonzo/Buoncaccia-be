package it.buonacaccia.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.service.RegionSettingsService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.RegionSettingsDTO;
import it.buonacaccia.v1.mapper.RegionSettingsMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/region-settings")
@Tag(name = "region-settings", description = "Coordinate di pagamento e contatti delle segreterie")
public class RegionSettingsController {

    private final RegionSettingsService service;

    @GetMapping("/{key}")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Coordinate di una regione (sigla) o nazionali (NAT); campi vuoti se mai impostate")
    public ApiResponse<RegionSettingsDTO> get(@PathVariable String key) {
        return ApiResponse.ok(RegionSettingsMapper.INSTANCE.toDto(service.get(key)));
    }

    @PutMapping("/{key}")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Imposta le coordinate: la segreteria le proprie, l'amministrazione tutte (FR-016)")
    public ApiResponse<RegionSettingsDTO> save(@PathVariable String key, @Valid @RequestBody RegionSettingsDTO request,
                                               @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(RegionSettingsMapper.INSTANCE.toDto(service.save(CallerContext.from(jwt), key, request)));
    }
}
