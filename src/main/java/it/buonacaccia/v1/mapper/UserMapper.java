package it.buonacaccia.v1.mapper;

import it.buonacaccia.v1.dto.UserProfileResponse;
import it.buonacaccia.v1.dto.UserProfileResponse.CensusResponse;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.User;
import org.springframework.stereotype.Component;

/** Conversioni utente ↔ DTO. */
@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.id(), user.agesciUUID(), user.username(), user.firstName(),
                user.lastName(), user.email(), user.status(), user.isMember(), toCensus(user.census()),
                user.roles(), user.entitlements());
    }

    private static CensusResponse toCensus(Census census) {
        if (census == null) {
            return null;
        }
        return new CensusResponse(census.memberCode(), census.groupId(), census.unitId(), census.branch(),
                census.memberType(), census.status(), census.year());
    }
}
