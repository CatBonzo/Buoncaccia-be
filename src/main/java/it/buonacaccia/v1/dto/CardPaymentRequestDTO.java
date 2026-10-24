package it.buonacaccia.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Pagamento con carta del socio (feature 018, simulato): arrivano solo le ultime quattro cifre, mai il numero intero. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CardPaymentRequestDTO {

    @NotBlank
    @Pattern(regexp = "\\d{4}", message = "Servono le ultime quattro cifre della carta.")
    private String cardLast4;
}
