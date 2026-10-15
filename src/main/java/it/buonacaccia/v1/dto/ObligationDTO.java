package it.buonacaccia.v1.dto;

import java.util.List;

import it.buonacaccia.model.DocumentDelivery;
import it.buonacaccia.model.ObligationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "[A-Z0-9_]{1,30}", message = "Il codice è fatto di lettere maiuscole, cifre e _, al massimo 30.")
    private String code;
    private DocumentDelivery delivery;
    private List<String> allowedMimeTypes;
    @Min(value = 1, message = "La dimensione massima è almeno 1 byte.")
    @Max(value = 10_485_760, message = "La dimensione massima è 10 MB.")
    private Long maxSizeBytes;
}
