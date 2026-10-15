package it.buonacaccia.v1.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Quota, versato e residuo di un'iscrizione, in euro (FR-001 della 005). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class PaymentSummaryDTO {

    private BigDecimal fee;
    private BigDecimal paid;
    private BigDecimal due;
}
