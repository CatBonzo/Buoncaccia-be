package it.buonacaccia.v1.mapper;

import it.buonacaccia.model.Census;
import it.buonacaccia.model.User;
import it.buonacaccia.v1.dto.CensusResponse;
import it.buonacaccia.v1.dto.UserProfileResponse;
import org.springframework.stereotype.Component;

/** Conversioni utente ↔ DTO. */
@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .buonaCacciaUUID(user.getId())
                .agesciUUID(user.getAgesciUUID())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .status(user.getStatus())
                .member(user.isMember())
                .census(toCensus(user.getCensus()))
                .roles(user.getRoles())
                .entitlements(user.getEntitlements())
                .build();
    }

    private static CensusResponse toCensus(Census census) {
        if (census == null) {
            return null;
        }
        return CensusResponse.builder()
                .memberCode(census.getMemberCode())
                .groupId(census.getGroupId())
                .unitId(census.getUnitId())
                .branch(census.getBranch())
                .memberType(census.getMemberType())
                .status(census.getStatus())
                .year(census.getYear())
                .build();
    }
}
