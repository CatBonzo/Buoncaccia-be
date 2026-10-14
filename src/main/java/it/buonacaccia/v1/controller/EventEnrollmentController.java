package it.buonacaccia.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.service.EnrollmentService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.EnrollmentDTO;
import it.buonacaccia.v1.dto.EnrollmentPageDTO;
import it.buonacaccia.v1.dto.EnrollmentRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Iscrizioni viste dall'evento: iscriversi (US1, US2) e l'elenco degli iscritti per la segreteria (US5). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events/{eventId}/enrollments")
@Tag(name = "enrollments", description = "Iscrizioni agli eventi e lista d'attesa")
public class EventEnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityAccess(entitlement = Entitlements.ISCRIZIONE_SELF)
    @Operation(summary = "Iscrive sé stessi (corpo vuoto) o un ragazzo della propria unità (memberCode)")
    public ApiResponse<EnrollmentDTO> enroll(@PathVariable String eventId,
                                             @Valid @RequestBody(required = false) EnrollmentRequestDTO request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.enroll(CallerContext.from(jwt), eventId, request));
    }

    @GetMapping
    @SecurityAccess(entitlement = Entitlements.ISCRIZIONE_GESTIONE)
    @Operation(summary = "Iscritti dell'evento in ordine di arrivo, per chi lo gestisce (FR-023)")
    public ApiResponse<EnrollmentPageDTO> list(@PathVariable String eventId,
                                               @RequestParam(required = false) EnrollmentStatus status,
                                               @RequestParam(required = false) SeatPosition position,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(enrollmentService.listForEvent(CallerContext.from(jwt), eventId, status, position, page, size));
    }
}
