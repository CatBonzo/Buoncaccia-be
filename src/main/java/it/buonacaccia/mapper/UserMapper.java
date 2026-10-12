package it.buonacaccia.mapper;

import it.buonacaccia.api.dto.MockUserResponse;
import it.buonacaccia.api.dto.UserProfileResponse;
import it.buonacaccia.api.dto.UserProfileResponse.CensusResponse;
import it.buonacaccia.config.MockAuthConfig;
import it.buonacaccia.config.MockedIdentityProperties.MockedUser;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.User;
import it.buonacaccia.service.MockIdentityService.IssuedMockUser;
import org.springframework.stereotype.Component;

/** Conversioni utente ↔ DTO. */
@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.id(), user.agesciUUID(), user.username(), user.firstName(),
                user.lastName(), user.email(), user.status(), user.isMember(), toCensus(user.census()),
                user.roles(), user.entitlements());
    }

    public MockUserResponse toMockUser(IssuedMockUser issued) {
        MockedUser entry = issued.entry();
        return new MockUserResponse(entry.agesciUUID(), issued.buonaCacciaUUID(), entry.label(), entry.username(),
                entry.firstName(), entry.lastName(), entry.email(), entry.authentication(), entry.census(),
                MockAuthConfig.ISSUER, issued.accessToken());
    }

    private static CensusResponse toCensus(Census census) {
        if (census == null) {
            return null;
        }
        return new CensusResponse(census.memberCode(), census.groupId(), census.unitId(), census.branch(),
                census.memberType(), census.status(), census.year());
    }
}
