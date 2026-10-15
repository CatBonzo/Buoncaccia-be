package it.buonacaccia.v1.dto;

import java.time.Instant;
import java.util.List;

import it.buonacaccia.model.DocumentDelivery;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Adempimento come lo vede la pagina di stato. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EnrollmentObligationDTO {

    private ObligationType type;
    private String description;
    private ObligationStatus status;
    private Instant satisfiedAt;
    private String note;
    private String code;
    private DocumentDelivery delivery;
    private List<String> allowedMimeTypes;
    private Long maxSizeBytes;
}
