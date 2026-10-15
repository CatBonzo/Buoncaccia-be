package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.FormSection;
import it.buonacaccia.model.FormTemplate;
import it.buonacaccia.v1.dto.FormSectionDTO;
import it.buonacaccia.v1.dto.FormTemplateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FormMapper {

    FormMapper INSTANCE = Mappers.getMapper(FormMapper.class);

    FormTemplateDTO toDto(FormTemplate template);

    List<FormSection> toSections(List<FormSectionDTO> sections);

    List<FormSectionDTO> toSectionDtos(List<FormSection> sections);
}
