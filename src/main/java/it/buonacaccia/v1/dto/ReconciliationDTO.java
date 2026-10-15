package it.buonacaccia.v1.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Quadro dei pagamenti di un evento (FR-014 della 005): totali in euro, residui, pagamenti e rimborsi. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ReconciliationDTO {

    private String eventId;
    private String eventCode;
    /** Pagamenti validi. */
    private BigDecimal collected;
    /** Pagamenti annullati. */
    private BigDecimal cancelled;
    /** Rimborsi richiesti o in lavorazione. */
    private BigDecimal toRefund;
    /** Rimborsi emessi. */
    private BigDecimal refunded;
    private List<Outstanding> outstanding;
    private List<PaymentDTO> payments;
    private List<RefundDTO> refunds;

    /** Iscrizione attiva nei posti con ancora qualcosa da pagare. */
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @ToString
    @Builder
    public static class Outstanding {

        private String enrollmentNumber;
        private String participantName;
        private BigDecimal fee;
        private BigDecimal paid;
        private BigDecimal due;
    }
}
