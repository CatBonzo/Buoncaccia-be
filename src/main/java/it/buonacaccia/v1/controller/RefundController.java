package it.buonacaccia.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.RefundStatus;
import it.buonacaccia.service.RefundService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.RefundDTO;
import it.buonacaccia.v1.dto.RefundIssueDTO;
import it.buonacaccia.v1.dto.RefundPageDTO;
import it.buonacaccia.v1.dto.ReasonDTO;
import it.buonacaccia.v1.mapper.RefundMapper;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
@Tag(name = "refunds", description = "Rimborsi lavorati dalla segreteria")
public class RefundController {

    private final RefundService refundService;

    @GetMapping
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Coda dei rimborsi della propria regione, dal più vecchio (FR-011)")
    public ApiResponse<RefundPageDTO> list(@RequestParam(required = false) RefundStatus status,
                                           @RequestParam(required = false) String eventId,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(refundService.list(CallerContext.from(jwt), status, eventId, page, size));
    }

    @PostMapping("/{id}/take")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Prende in carico il rimborso (REQUESTED → IN_PROGRESS)")
    public ApiResponse<RefundDTO> take(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(RefundMapper.INSTANCE.toDto(refundService.take(CallerContext.from(jwt), id)));
    }

    @PostMapping("/{id}/issue")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Segna il rimborso emesso, con data e riferimento (→ ISSUED)")
    public ApiResponse<RefundDTO> issue(@PathVariable String id, @Valid @RequestBody RefundIssueDTO request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(RefundMapper.INSTANCE.toDto(refundService.issue(CallerContext.from(jwt), id, request)));
    }

    @PostMapping("/{id}/reject")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Rifiuta il rimborso con un motivo (→ REJECTED)")
    public ApiResponse<RefundDTO> reject(@PathVariable String id, @Valid @RequestBody ReasonDTO request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(RefundMapper.INSTANCE.toDto(refundService.reject(CallerContext.from(jwt), id, request.getReason())));
    }
}
