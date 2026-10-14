package it.buonacaccia.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.notification.AuthorizationLinkService;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.AuthorizationLinkDTO;
import it.buonacaccia.v1.dto.LinkOutcomeDTO;
import it.buonacaccia.v1.dto.ReasonDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autorizzazioni dei capi dal link nell'e-mail (US2 della 004). API pubbliche: la credenziale è il
 * token nel percorso. Un link non valido risponde sempre 404 {@code 40420} (FR-018).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/authorization-links")
@Tag(name = "authorization-links", description = "Autorizzazioni dei capi dal link ricevuto per e-mail")
public class AuthorizationLinkController {

    private final AuthorizationLinkService linkService;

    @GetMapping("/{token}")
    @Operation(summary = "Mostra l'iscrizione e la domanda, senza cambiare nulla (FR-016)")
    public ApiResponse<AuthorizationLinkDTO> read(@PathVariable String token) {
        return ApiResponse.ok(linkService.read(token));
    }

    @PostMapping("/{token}/approve")
    @Operation(summary = "Il capo autorizza; stesso effetto dell'approvazione dal sito (FR-019)")
    public ApiResponse<LinkOutcomeDTO> approve(@PathVariable String token) {
        return ApiResponse.ok(linkService.approve(token));
    }

    @PostMapping("/{token}/reject")
    @Operation(summary = "Il capo nega con un motivo; l'iscrizione è rifiutata")
    public ApiResponse<LinkOutcomeDTO> reject(@PathVariable String token, @Valid @RequestBody ReasonDTO request) {
        return ApiResponse.ok(linkService.reject(token, request.getReason()));
    }
}
