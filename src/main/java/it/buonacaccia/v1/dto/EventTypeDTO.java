package it.buonacaccia.v1.dto;

import java.util.List;

import it.buonacaccia.model.EventCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Tipo evento in richiesta e in risposta; {@code active} si cambia solo con la disattivazione (FR-001). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventTypeDTO {

    @NotBlank(message = "Il codice è obbligatorio.")
    @Pattern(regexp = "^[A-Z0-9_]{2,20}$", message = "Da 2 a 20 tra lettere maiuscole, cifre e _.")
    private String code;
    @NotNull(message = "La categoria è obbligatoria.")
    private EventCategory category;
    @Valid
    private RequirementsDTO defaultRequirements;
    private List<@Valid ObligationDTO> defaultObligations;
    private Boolean application;
    private Boolean active;
}
