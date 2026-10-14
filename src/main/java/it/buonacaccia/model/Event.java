package it.buonacaccia.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Evento del catalogo (FR-004). In bozza quasi tutto è facoltativo; gli invarianti si controllano
 * alla pubblicazione (FR-007). Lo stato effettivo non è salvato: si calcola da {@link #lifecycle},
 * dalla chiusura anticipata e dai quattro istanti, ricalcolati dalle date a ogni salvataggio (research R1).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("events")
@CompoundIndex(name = "lifecycle_startsAt", def = "{'lifecycle': 1, 'startsAt': 1}")
@CompoundIndex(name = "region_startsAt", def = "{'region': 1, 'startsAt': 1}")
@CompoundIndex(name = "typeCode_startsAt", def = "{'typeCode': 1, 'startsAt': 1}")
public class Event {

    @Id
    private String id;

    /** Codice leggibile {@code PIE-2027-001} (research R5). */
    @Indexed(unique = true)
    private String code;

    private String typeCode;
    private String title;
    private String description;
    private EventLevel level;
    /** Vuota solo per gli eventi nazionali. */
    private Region region;
    /** Testo libero, solo per il livello zona. */
    private String zone;
    private String location;
    /** Sigla di due lettere. */
    private String province;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;

    private Integer seats;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal fee;
    private EnrollmentMode enrollmentMode;
    private Requirements requirements;
    @Builder.Default
    private List<Obligation> obligations = new ArrayList<>();
    private Contacts contacts;

    private EventLifecycle lifecycle;
    /** Chiusura anticipata delle iscrizioni (FR-015). */
    private Instant registrationClosedEarlyAt;
    private String cancellationReason;
    private Instant cancelledAt;

    /** Apertura iscrizioni: 09:00 Europe/Rome del giorno di apertura. */
    private Instant registrationOpensAt;
    /** Chiusura iscrizioni: fine del giorno di chiusura, Europe/Rome. */
    private Instant registrationClosesAt;
    /** Inizio del giorno di partenza, Europe/Rome. */
    private Instant startsAt;
    /** Fine del giorno di rientro, Europe/Rome. */
    private Instant endsAt;

    /** Versione per la concorrenza ottimistica (FR-018, research R4). */
    @Version
    private Long version;

    /** {@code sub} del token di chi ha creato l'evento. */
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
