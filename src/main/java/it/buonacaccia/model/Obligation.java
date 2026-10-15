package it.buonacaccia.model;

import java.util.List;

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

    /** Codice del documento, univoco nell'evento (feature 006); assente = {@code DOC}. */
    private String code;
    /** Solo per i documenti: online o cartaceo; assente = online. */
    private DocumentDelivery delivery;
    /** Solo per i documenti online: tipi ammessi; assente = JPEG, PNG, PDF. */
    private List<String> allowedMimeTypes;
    /** Solo per i documenti online: dimensione massima in byte; assente = 5 MB. */
    private Long maxSizeBytes;
}
