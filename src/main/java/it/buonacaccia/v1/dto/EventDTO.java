package it.buonacaccia.v1.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import it.buonacaccia.model.Availability;
import it.buonacaccia.model.EnrollmentMode;
import it.buonacaccia.model.EventAction;
import it.buonacaccia.model.EventCategory;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.EventStatus;
import it.buonacaccia.model.Region;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Evento in risposta (FR-020): dati pubblici, stato effettivo e indicatore calcolati all'ora della
 * richiesta. Gli istanti interni (research R1) non escono.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventDTO {

    private String id;
    private String code;
    private String typeCode;
    private EventCategory category;
    private String title;
    private String description;
    private EventLevel level;
    private Region region;
    private String zone;
    private String location;
    private String province;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;
    private Integer seats;
    private BigDecimal fee;
    private EnrollmentMode enrollmentMode;
    private RequirementsDTO requirements;
    private List<ObligationDTO> obligations;
    private List<FormSectionDTO> formSections;
    private boolean medicalFormRequired;
    private boolean dietaryInfoRequired;
    private ContactsDTO contacts;
    private EventStatus status;
    private Availability availability;
    private int enrolledCount;
    /** Cosa può fare ora chi gestisce l'evento; vuoto per tutti gli altri e nella ricerca pubblica. */
    private List<EventAction> availableActions;
    private String cancellationReason;
    private Instant cancelledAt;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
