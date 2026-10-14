package it.buonacaccia.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Un campo da correggere, nel {@code payload} delle risposte 422 (research R7). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FieldErrorDTO {

    private String field;
    private String message;
}
