package it.buonacaccia.v1.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.service.EnrollmentService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.EnrollmentDTO;
import it.buonacaccia.v1.dto.EnrollmentPageDTO;
import it.buonacaccia.v1.dto.NoteDTO;
import it.buonacaccia.v1.dto.NotificationDTO;
import it.buonacaccia.v1.dto.ReasonDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Iscrizione singola ed elenchi personali (US2–US5). Chi può vedere o agire su un'iscrizione lo
 * decide il service con la relazione fra persona e iscrizione (FR-021).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/enrollments")
@Tag(name = "enrollments", description = "Iscrizioni agli eventi e lista d'attesa")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping("/mine")
    @Operation(summary = "Le mie iscrizioni, dalla più recente (FR-022)")
    public ApiResponse<EnrollmentPageDTO> mine(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.mine(CallerContext.from(jwt), page, size));
    }

    @GetMapping("/presented")
    @Operation(summary = "Iscrizioni che ho presentato come capo (FR-022)")
    public ApiResponse<EnrollmentPageDTO> presented(@RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.presented(CallerContext.from(jwt), page, size));
    }

    @GetMapping("/to-authorize")
    @Operation(summary = "Iscrizioni attive con un'autorizzazione da dare da parte mia")
    public ApiResponse<EnrollmentPageDTO> toAuthorize(@RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.toAuthorize(CallerContext.from(jwt), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pagina di stato dell'iscrizione (FR-021)")
    public ApiResponse<EnrollmentDTO> get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.get(CallerContext.from(jwt), id));
    }

    @GetMapping("/{id}/notifications")
    @SecurityAccess(entitlement = Entitlements.ISCRIZIONE_GESTIONE)
    @Operation(summary = "Registro degli avvisi dell'iscrizione, per chi gestisce l'evento (FR-007 della 004)")
    public ApiResponse<List<NotificationDTO>> notifications(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.notifications(CallerContext.from(jwt), id));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Ritiro del partecipante o del presentatore prima della partenza (FR-018, FR-019)")
    public ApiResponse<EnrollmentDTO> withdraw(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.withdraw(CallerContext.from(jwt), id));
    }

    @PostMapping("/{id}/obligations/{type}/approve")
    @Operation(summary = "Il capo unità o il capo gruppo del partecipante autorizza (FR-009)")
    public ApiResponse<EnrollmentDTO> approve(@PathVariable String id, @PathVariable ObligationType type,
                                              @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.approve(CallerContext.from(jwt), id, type));
    }

    @PostMapping("/{id}/obligations/{type}/reject")
    @Operation(summary = "Diniego motivato: l'iscrizione diventa REJECTED (FR-009)")
    public ApiResponse<EnrollmentDTO> reject(@PathVariable String id, @PathVariable ObligationType type,
                                             @Valid @RequestBody ReasonDTO request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.reject(CallerContext.from(jwt), id, type, request.getReason()));
    }

    @PostMapping("/{id}/obligations/{type}/satisfy")
    @SecurityAccess(entitlement = Entitlements.ISCRIZIONE_GESTIONE)
    @Operation(summary = "La segreteria che gestisce l'evento segna l'adempimento come soddisfatto (FR-010)")
    public ApiResponse<EnrollmentDTO> satisfy(@PathVariable String id, @PathVariable ObligationType type,
                                              @Valid @RequestBody(required = false) NoteDTO request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.satisfy(CallerContext.from(jwt), id, type,
                request == null ? null : request.getNote()));
    }
}
