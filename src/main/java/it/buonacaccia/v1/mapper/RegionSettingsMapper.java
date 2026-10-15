package it.buonacaccia.v1.mapper;

import it.buonacaccia.model.RegionSettings;
import it.buonacaccia.v1.dto.RegionSettingsDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RegionSettingsMapper {

    RegionSettingsMapper INSTANCE = Mappers.getMapper(RegionSettingsMapper.class);

    @Mapping(target = "key", source = "id")
    RegionSettingsDTO toDto(RegionSettings settings);
}
