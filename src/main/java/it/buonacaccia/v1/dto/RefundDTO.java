package it.buonacaccia.v1.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Richiesta di rimborso con il suo stato (FR-009, FR-010 della 005). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class RefundDTO {

    private String id;
    private String enrollmentId;
    private String enrollmentNumber;
    private String participantId;
    private String participantName;
    private String eventId;
    private String eventCode;
    private BigDecimal amount;
    private ClosingReason closingReason;
    private RefundStatus status;
    private Instant createdAt;
    private String takenBy;
    private Instant takenAt;
    private LocalDate issuedOn;
    private String reference;
    private String rejectReason;
    private String processedBy;
    private Instant processedAt;
}
