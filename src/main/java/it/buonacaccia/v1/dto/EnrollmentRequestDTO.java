package it.buonacaccia.v1.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Richiesta di iscrizione: senza {@code memberCode} ci si iscrive da soli, con il codice il capo iscrive un ragazzo. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EnrollmentRequestDTO {

    @Size(max = 20)
    private String memberCode;
}
