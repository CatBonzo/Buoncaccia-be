package it.buonacaccia.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Annullamento motivato (FR-017). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CancelDTO {

    @NotBlank(message = "Il motivo è obbligatorio.")
    @Size(max = 500, message = "Al massimo 500 caratteri.")
    private String reason;
    @NotNull(message = "Indica la versione letta dell'evento.")
    private Long version;
}
