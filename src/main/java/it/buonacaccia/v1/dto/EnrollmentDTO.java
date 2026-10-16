package it.buonacaccia.v1.dto;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.SeatPosition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Pagina di stato dell'iscrizione (FR-021). Titolo e data dell'evento, posizione in attesa e azioni
 * disponibili li calcola il service a ogni lettura.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EnrollmentDTO {

    private String id;
    private String number;
    private String eventId;
    private String eventCode;
    private String eventTitle;
    private LocalDate eventStartDate;
    private String participantId;
    private String participantMemberCode;
    private String participantName;
    private String presenterId;
    private String presenterName;
    private EnrollmentStatus status;
    private SeatPosition position;
    private Integer waitingPosition;
    private long arrivalOrder;
    private boolean active;
    private List<EnrollmentObligationDTO> obligations;
    private Instant registeredAt;
    private Instant deadline;
    private Instant promotedAt;
    private Instant completedAt;
    private Instant closedAt;
    private ClosingReason closingReason;
    private String closingNote;
    private Boolean refundDue;
    /** Quota, versato e residuo; assente per eventi gratuiti. */
    private PaymentSummaryDTO payment;
    /** Dove pagare; presente solo se il pagamento è ancora da fare. */
    private PaymentInfoDTO paymentInfo;
    /** Rimborso aperto alla chiusura dell'iscrizione, se c'è. */
    private RefundSummaryDTO refund;
    /** Cosa può fare ora chi guarda: i valori possibili sono elencati nel contratto. */
    @ArraySchema(schema = @Schema(allowableValues = {"WITHDRAW", "SATISFY", "APPROVE_UNIT_LEADER", "REJECT_UNIT_LEADER",
            "APPROVE_GROUP_LEADER", "REJECT_GROUP_LEADER"}))
    private List<String> availableActions;
    private Long version;
}
