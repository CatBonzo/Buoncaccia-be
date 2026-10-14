package it.buonacaccia.v1.dto;

import java.time.Instant;
import java.time.LocalDate;

import it.buonacaccia.model.ObligationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Ciò che il capo vede aprendo il link di autorizzazione (FR-016 della 004). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AuthorizationLinkDTO {

    private String enrollmentNumber;
    private String eventCode;
    private String eventTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private String participantName;
    private String presenterName;
    private ObligationType obligationType;
    /** Il capo a cui è stato inviato il link. */
    private String leaderName;
    private Instant deadline;
}
