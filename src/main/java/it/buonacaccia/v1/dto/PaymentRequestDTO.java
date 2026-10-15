package it.buonacaccia.v1.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.buonacaccia.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Pagamento ricevuto dalla segreteria (FR-005 della 005): importo in euro con al massimo due decimali. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class PaymentRequestDTO {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotNull
    private PaymentMethod method;

    @NotNull
    private LocalDate receivedOn;

    @Size(max = 100)
    private String reference;
}
