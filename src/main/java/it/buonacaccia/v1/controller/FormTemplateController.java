package it.buonacaccia.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.service.FormTemplateService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.FormTemplateDTO;
import it.buonacaccia.v1.mapper.FormMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "forms", description = "Scheda elettronica: modelli e schede compilate")
public class FormTemplateController {

    private final FormTemplateService formTemplateService;

    @GetMapping("/api/v1/event-types/{code}/form-template")
    @Operation(summary = "Modello di scheda di un tipo evento (FR-001)")
    public ApiResponse<FormTemplateDTO> get(@PathVariable String code) {
        return ApiResponse.ok(FormMapper.INSTANCE.toDto(formTemplateService.get(code)));
    }

    @PutMapping("/api/v1/event-types/{code}/form-template")
    @SecurityAccess(entitlement = Entitlements.TIPOEVENTO_GESTIONE)
    @Operation(summary = "L'amministrazione salva il modello di scheda di un tipo evento (FR-001)")
    public ApiResponse<FormTemplateDTO> save(@PathVariable String code, @Valid @RequestBody FormTemplateDTO request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(FormMapper.INSTANCE.toDto(formTemplateService.save(CallerContext.from(jwt), code, request)));
    }
}
