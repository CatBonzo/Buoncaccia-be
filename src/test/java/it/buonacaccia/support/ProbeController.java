package it.buonacaccia.support;

import it.buonacaccia.api.dto.ApiResponse;
import it.buonacaccia.config.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.Roles;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operazioni di prova, solo nei test (profilo {@code probe}): servono a verificare la guardia
 * e l'envelope prima che esistano gli endpoint veri delle fasi successive.
 */
@Profile("probe")
@RestController
@RequestMapping("/api/v1/probe")
class ProbeController {

    @GetMapping("/entitlement")
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    ApiResponse<String> entitlement() {
        return ApiResponse.ok("ok");
    }

    @GetMapping("/role")
    @SecurityAccess(role = Roles.AMMINISTRATORE)
    ApiResponse<String> role() {
        return ApiResponse.ok("ok");
    }

    @GetMapping("/token")
    @SecurityAccess
    ApiResponse<String> token() {
        return ApiResponse.ok("ok");
    }

    @GetMapping("/boom")
    ApiResponse<String> boom() {
        throw new IllegalStateException("dettaglio interno che non deve uscire");
    }
}
