package it.buonacaccia.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.service.FormService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.FormAnswersDTO;
import it.buonacaccia.v1.dto.FormDTO;
import it.buonacaccia.v1.dto.ReasonDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "forms", description = "Scheda elettronica: modelli e schede compilate")
public class FormController {

    private final FormService formService;

    @GetMapping("/api/v1/enrollments/{id}/form")
    @Operation(summary = "Scheda dell'iscrizione: il partecipante sempre, la segreteria dell'evento se inviata (FR-003, FR-008)")
    public ApiResponse<FormDTO> get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(formService.get(CallerContext.from(jwt), id));
    }

    @PutMapping("/api/v1/enrollments/{id}/form")
    @Operation(summary = "Il partecipante salva la bozza della scheda (FR-003)")
    public ApiResponse<FormDTO> save(@PathVariable String id, @Valid @RequestBody FormAnswersDTO request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(formService.save(CallerContext.from(jwt), id, request.getAnswers()));
    }

    @PostMapping("/api/v1/enrollments/{id}/form/submit")
    @Operation(summary = "Il partecipante invia la scheda (FR-005)")
    public ApiResponse<FormDTO> submit(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(formService.submit(CallerContext.from(jwt), id));
    }

    @PostMapping("/api/v1/enrollments/{id}/form/reopen")
    @SecurityAccess(entitlement = Entitlements.ISCRIZIONE_GESTIONE)
    @Operation(summary = "La segreteria riapre una scheda inviata con un motivo (FR-007)")
    public ApiResponse<FormDTO> reopen(@PathVariable String id, @Valid @RequestBody ReasonDTO request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(formService.reopen(CallerContext.from(jwt), id, request.getReason()));
    }
}
