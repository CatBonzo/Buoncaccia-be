package it.buonacaccia.v1.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.service.PaymentService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.PaymentDTO;
import it.buonacaccia.v1.dto.PaymentRequestDTO;
import it.buonacaccia.v1.dto.ReasonDTO;
import it.buonacaccia.v1.mapper.PaymentMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "payments", description = "Pagamenti registrati dalla segreteria")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/v1/enrollments/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "La segreteria registra un pagamento ricevuto (FR-005)")
    public ApiResponse<PaymentDTO> register(@PathVariable String id, @Valid @RequestBody PaymentRequestDTO request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(PaymentMapper.INSTANCE.toDto(paymentService.register(CallerContext.from(jwt), id, request)));
    }

    @GetMapping("/api/v1/enrollments/{id}/payments")
    @Operation(summary = "Pagamenti di un'iscrizione, per partecipante, presentatore e segreteria")
    public ApiResponse<List<PaymentDTO>> list(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(PaymentMapper.INSTANCE.toDtoList(paymentService.list(CallerContext.from(jwt), id)));
    }

    @PostMapping("/api/v1/payments/{id}/cancel")
    @SecurityAccess(entitlement = Entitlements.PAGAMENTO_REGISTRAZIONE)
    @Operation(summary = "Annulla un pagamento registrato per errore, con il motivo (FR-006)")
    public ApiResponse<PaymentDTO> cancel(@PathVariable String id, @Valid @RequestBody ReasonDTO request,
                                          @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(PaymentMapper.INSTANCE.toDto(paymentService.cancel(CallerContext.from(jwt), id, request.getReason())));
    }
}
