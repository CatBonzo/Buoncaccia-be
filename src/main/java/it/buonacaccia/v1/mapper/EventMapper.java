package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.Contacts;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.Obligation;
import it.buonacaccia.model.Requirements;
import it.buonacaccia.v1.dto.ContactsDTO;
import it.buonacaccia.v1.dto.EventDTO;
import it.buonacaccia.v1.dto.EventRequestDTO;
import it.buonacaccia.v1.dto.ObligationDTO;
import it.buonacaccia.v1.dto.RequirementsDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * Conversioni dell'evento. Stato, disponibilità, nome e categoria del tipo li aggiunge il service,
 * perché dipendono dall'ora e dal tipo evento.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    EventMapper INSTANCE = Mappers.getMapper(EventMapper.class);

    @Mapping(target = "version", ignore = true)
    Event toEntity(EventRequestDTO dto);

    /** Bozza: tutti i campi dalla richiesta (FR-008). */
    @Mapping(target = "version", ignore = true)
    void update(EventRequestDTO dto, @MappingTarget Event event);

    EventDTO toDto(Event event);

    Requirements toRequirements(RequirementsDTO dto);

    List<Obligation> toObligations(List<ObligationDTO> dtos);

    Contacts toContacts(ContactsDTO dto);
}
