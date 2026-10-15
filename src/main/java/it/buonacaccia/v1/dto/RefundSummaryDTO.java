package it.buonacaccia.v1.dto;

import java.math.BigDecimal;

import it.buonacaccia.model.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Stato e importo del rimborso, dentro la pagina dell'iscrizione. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class RefundSummaryDTO {

    private RefundStatus status;
    private BigDecimal amount;
}
