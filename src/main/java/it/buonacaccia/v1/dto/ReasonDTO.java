package it.buonacaccia.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Motivo obbligatorio, es. del diniego di un'autorizzazione (FR-009). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ReasonDTO {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
