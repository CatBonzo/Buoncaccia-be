package it.buonacaccia.v1.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import it.buonacaccia.model.PaymentMethod;
import it.buonacaccia.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Un pagamento del registro: quelli annullati restano con il motivo (FR-006 della 005). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class PaymentDTO {

    private String id;
    private String enrollmentId;
    private String enrollmentNumber;
    private BigDecimal amount;
    private PaymentMethod method;
    private LocalDate receivedOn;
    private String reference;
    private PaymentStatus status;
    private String registeredBy;
    private Instant registeredAt;
    private String cancelledBy;
    private Instant cancelledAt;
    private String cancelReason;
}
