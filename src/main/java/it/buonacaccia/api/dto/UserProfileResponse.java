package it.buonacaccia.api.dto;

import java.util.List;

import it.buonacaccia.model.Branch;
import it.buonacaccia.model.CensusStatus;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.UserStatus;

/** Profilo di {@code GET /api/v1/users/me} (FR-010). {@code member} = ha i dati di censimento. */
public record UserProfileResponse(
        String buonaCacciaUUID,
        String agesciUUID,
        String username,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        boolean member,
        CensusResponse census,
        List<String> roles,
        List<String> entitlements) {

    public record CensusResponse(
            String memberCode,
            String groupId,
            String unitId,
            Branch branch,
            MemberType memberType,
            CensusStatus status,
            Integer year) {
    }
}
