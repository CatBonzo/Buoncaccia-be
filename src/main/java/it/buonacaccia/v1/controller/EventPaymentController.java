package it.buonacaccia.v1.controller;

import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.service.ReconciliationService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.ReconciliationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "reconciliation", description = "Quadro dei pagamenti di un evento")
public class EventPaymentController {

    private final ReconciliationService reconciliationService;

    @GetMapping("/api/v1/events/{eventId}/reconciliation")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Totali, iscrizioni con residuo, pagamenti e rimborsi dell'evento (FR-014)")
    public ApiResponse<ReconciliationDTO> reconciliation(@PathVariable String eventId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(reconciliationService.get(CallerContext.from(jwt), eventId));
    }

    @GetMapping("/api/v1/events/{eventId}/reconciliation/export")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Lo stesso quadro in CSV: una riga per pagamento e per rimborso (FR-015)")
    public ResponseEntity<byte[]> export(@PathVariable String eventId, @AuthenticationPrincipal Jwt jwt) {
        String csv = reconciliationService.csv(CallerContext.from(jwt), eventId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("riconciliazione-" + eventId + ".csv").build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
