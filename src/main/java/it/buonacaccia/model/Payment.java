package it.buonacaccia.model;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pagamento registrato dalla segreteria su un'iscrizione (collezione {@code payments}, feature 005).
 * L'importo è in centesimi (research R1). Livello e regione dell'evento sono copiati dall'iscrizione
 * per la regola territoriale senza rileggere l'evento.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("payments")
@CompoundIndex(name = "enrollment_registered", def = "{'enrollmentId': 1, 'registeredAt': 1}")
public class Payment {

    @Id
    private String id;

    private String enrollmentId;
    private String enrollmentNumber;

    @Indexed
    private String eventId;
    private String eventCode;
    private EventLevel eventLevel;
    private Region eventRegion;
    private String participantId;

    private long amountCents;
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
