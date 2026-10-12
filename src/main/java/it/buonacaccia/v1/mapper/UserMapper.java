package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.Census;
import it.buonacaccia.model.User;
import it.buonacaccia.v1.dto.CensusResponse;
import it.buonacaccia.v1.dto.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/** Conversioni utente → DTO, generate da MapStruct (stile ScoutShopper-be). */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    // Model --> Dto
    @Mapping(target = "buonaCacciaUUID", source = "id")
    @Mapping(target = "member", expression = "java(user.isMember())")
    UserProfileResponse toDto(User user);

    // List<Model> --> List<Dto>
    List<UserProfileResponse> toDtoList(List<User> users);

    // Census --> Dto
    CensusResponse toCensusDto(Census census);
}
