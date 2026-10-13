package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.EventType;
import it.buonacaccia.v1.dto.EventTypeDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventTypeMapper {

    EventTypeMapper INSTANCE = Mappers.getMapper(EventTypeMapper.class);

    @Mapping(target = "active", ignore = true)
    EventType toEntity(EventTypeDTO dto);

    /** Copia nome, categoria, default e candidatura; codice e stato non cambiano. */
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "active", ignore = true)
    void update(EventTypeDTO dto, @MappingTarget EventType type);

    EventTypeDTO toDto(EventType type);

    List<EventTypeDTO> toDtoList(List<EventType> types);
}
