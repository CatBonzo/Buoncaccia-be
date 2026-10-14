package it.buonacaccia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Adempimento richiesto per l'iscrizione, es. un documento "foto JPG ≤ 1 MB" (FR-006). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode
public class Obligation {

    private ObligationType type;
    private String description;
}
