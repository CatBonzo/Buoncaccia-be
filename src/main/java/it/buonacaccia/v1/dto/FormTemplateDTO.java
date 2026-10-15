package it.buonacaccia.v1.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FormTemplateDTO {

    private String typeCode;
    private List<@Valid FormSectionDTO> sections;
    private String updatedBy;
    private Instant updatedAt;
}
