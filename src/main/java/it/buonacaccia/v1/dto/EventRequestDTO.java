package it.buonacaccia.v1.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import it.buonacaccia.model.EnrollmentMode;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.Region;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Dati di un evento inviati da chi lo gestisce (FR-004). In bozza tutto è facoltativo tranne tipo e
 * livello (FR-008); gli invarianti completi si controllano alla pubblicazione (FR-007).
 * {@code version} serve solo in modifica (FR-018).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventRequestDTO {

    @NotBlank(message = "Il tipo evento è obbligatorio.")
    private String typeCode;
    @Size(max = 200, message = "Al massimo 200 caratteri.")
    private String title;
    @Size(max = 5000, message = "Al massimo 5000 caratteri.")
    private String description;
    @NotNull(message = "Il livello è obbligatorio.")
    private EventLevel level;
    private Region region;
    @Size(max = 100, message = "Al massimo 100 caratteri.")
    private String zone;
    @Size(max = 200, message = "Al massimo 200 caratteri.")
    private String location;
    @Pattern(regexp = "^[A-Z]{2}$", message = "Sigla di due lettere maiuscole, es. TO.")
    private String province;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;
    @Min(value = 1, message = "I posti devono essere più di zero.")
    private Integer seats;
    @DecimalMin(value = "0", message = "La quota non può essere negativa.")
    @Digits(integer = 6, fraction = 2, message = "Quota in euro con al massimo due decimali.")
    private BigDecimal fee;
    private EnrollmentMode enrollmentMode;
    @Valid
    private RequirementsDTO requirements;
    private List<@Valid ObligationDTO> obligations;
    @Valid
    private ContactsDTO contacts;
    private List<@Valid FormSectionDTO> formSections;
    private Boolean medicalFormRequired;
    private Boolean dietaryInfoRequired;
    private Long version;
}
