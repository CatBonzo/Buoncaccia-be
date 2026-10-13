package it.buonacaccia.mock;

import it.buonacaccia.mock.MockedIdentityProperties.Authentication;
import it.buonacaccia.mock.MockedIdentityProperties.CensusData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Voce di {@code GET /api/v1/mock/auth/findAll}: dati del file più {@code issuer} e {@code accessToken}. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class MockUserResponse {

    private String agesciUUID;
    private String buonaCacciaUUID;
    private String label;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private Authentication authentication;
    private CensusData census;
    private String issuer;
    private String accessToken;
}
