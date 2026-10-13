package it.buonacaccia.v1.dto;

import it.buonacaccia.model.ObligationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ObligationDTO {

    @NotNull(message = "Indica il tipo di adempimento.")
    private ObligationType type;
    @Size(max = 300, message = "Al massimo 300 caratteri.")
    private String description;
}
