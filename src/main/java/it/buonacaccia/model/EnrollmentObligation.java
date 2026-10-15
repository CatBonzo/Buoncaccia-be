package it.buonacaccia.model;

import java.time.Instant;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Adempimento di una singola iscrizione, nato da quelli dell'evento (FR-007, research R7). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode
public class EnrollmentObligation {

    private ObligationType type;
    private String description;
    private ObligationStatus status;
    /** Id dell'utente che l'ha soddisfatto o negato. */
    private String satisfiedBy;
    private Instant satisfiedAt;
    /** Nota della segreteria o motivo del diniego. */
    private String note;

    /** Codice del documento, univoco nell'evento (feature 006); assente = {@code DOC}. */
    private String code;
    /** Solo per i documenti: online o cartaceo; assente = online. */
    private DocumentDelivery delivery;
    /** Solo per i documenti online: tipi ammessi; assente = JPEG, PNG, PDF. */
    private List<String> allowedMimeTypes;
    /** Solo per i documenti online: dimensione massima in byte; assente = 5 MB. */
    private Long maxSizeBytes;
}
