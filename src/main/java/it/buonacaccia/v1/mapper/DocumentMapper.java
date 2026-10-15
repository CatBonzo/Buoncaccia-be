package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.Document;
import it.buonacaccia.v1.dto.DocumentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DocumentMapper {

    DocumentMapper INSTANCE = Mappers.getMapper(DocumentMapper.class);

    DocumentDTO toDto(Document document);

    List<DocumentDTO> toDtoList(List<Document> documents);
}
