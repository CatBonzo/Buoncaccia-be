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
 * Richiesta di rimborso di un'iscrizione pagata che si è chiusa con diritto (collezione {@code refunds},
 * feature 005). Una sola per iscrizione (indice unico): nasce nella transazione di chiusura
 * (research R6). Le transizioni sono aggiornamenti condizionati sullo stato di partenza (R7).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("refunds")
@CompoundIndex(name = "status_created", def = "{'status': 1, 'createdAt': 1}")
public class Refund {

    @Id
    private String id;

    @Indexed(unique = true)
    private String enrollmentId;
    private String enrollmentNumber;
    private String participantId;
    private String participantName;
    private String presenterId;

    @Indexed
    private String eventId;
    private String eventCode;
    private EventLevel eventLevel;
    private Region eventRegion;

    private long amountCents;
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
