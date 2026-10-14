package it.buonacaccia.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Iscrizione di un socio a un evento (collezione {@code enrollments}, feature 003). Dell'evento si
 * copiano solo i campi che dopo la pubblicazione non cambiano; titolo, date e quota si leggono
 * dall'evento. L'indice parziale garantisce una sola iscrizione attiva per socio ed evento (R3).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("enrollments")
@CompoundIndex(name = "event_arrival", def = "{'eventId': 1, 'arrivalOrder': 1}", unique = true)
@CompoundIndex(name = "event_participant_active", def = "{'eventId': 1, 'participantId': 1}", unique = true,
        partialFilter = "{'active': true}")
@CompoundIndex(name = "unit_status", def = "{'participantUnitId': 1, 'status': 1}")
@CompoundIndex(name = "group_status", def = "{'participantGroupId': 1, 'status': 1}")
@CompoundIndex(name = "status_deadline", def = "{'status': 1, 'deadline': 1}")
public class Enrollment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String number;

    private String eventId;
    private String eventCode;
    private String eventTypeCode;
    private EventLevel eventLevel;
    private Region eventRegion;

    @Indexed
    private String participantId;
    private String participantMemberCode;
    private String participantName;
    private String participantGroupId;
    private String participantUnitId;

    @Indexed
    private String presenterId;
    private String presenterName;

    private EnrollmentStatus status;
    private SeatPosition position;
    private long arrivalOrder;
    private boolean active;

    @Builder.Default
    private List<EnrollmentObligation> obligations = new ArrayList<>();

    private Instant registeredAt;
    private Instant deadline;
    private Instant promotedAt;
    private Instant completedAt;
    private Instant closedAt;
    private ClosingReason closingReason;
    private String closingNote;
    /** Diritto al rimborso (BR-10): null finché l'iscrizione è attiva. */
    private Boolean refundDue;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
