package it.buonacaccia.v1.dto;

import it.buonacaccia.model.AuthorizationLink;
import it.buonacaccia.model.EnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Esito di una decisione presa dal link (FR-019 della 004). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class LinkOutcomeDTO {

    private String enrollmentNumber;
    private AuthorizationLink.Decision decision;
    private EnrollmentStatus enrollmentStatus;
}
