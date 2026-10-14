package it.buonacaccia.v1.mapper;

import it.buonacaccia.model.Enrollment;
import it.buonacaccia.v1.dto.EnrollmentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/** Conversioni dell'iscrizione. Dati dell'evento, posizione in attesa e azioni li aggiunge il service. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EnrollmentMapper {

    EnrollmentMapper INSTANCE = Mappers.getMapper(EnrollmentMapper.class);

    EnrollmentDTO toDto(Enrollment enrollment);
}
