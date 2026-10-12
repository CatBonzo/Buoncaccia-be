package it.buonacaccia.v1.dto;

import java.util.List;

import it.buonacaccia.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Profilo di {@code GET /api/v1/users/me} (FR-010). {@code member} = ha i dati di censimento. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class UserProfileDTO {

    private String buonaCacciaUUID;
    private String agesciUUID;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private UserStatus status;
    private boolean member;
    private CensusDTO census;
    private List<String> roles;
    private List<String> entitlements;
}
