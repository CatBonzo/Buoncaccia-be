package it.buonacaccia.mock;

import it.buonacaccia.mock.MockedIdentityProperties.Authentication;
import it.buonacaccia.mock.MockedIdentityProperties.CensusData;

/** Voce di {@code GET /api/v1/mock/auth/findAll}: dati del file più {@code issuer} e {@code accessToken}. */
public record MockUserResponse(
        String agesciUUID,
        String buonaCacciaUUID,
        String label,
        String username,
        String firstName,
        String lastName,
        String email,
        Authentication authentication,
        CensusData census,
        String issuer,
        String accessToken) {
}
