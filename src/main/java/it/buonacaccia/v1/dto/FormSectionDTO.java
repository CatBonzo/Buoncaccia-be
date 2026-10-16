package it.buonacaccia.v1.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
public class FormSectionDTO {

    @Size(max = 200, message = "Al massimo 200 caratteri.")
    private String title;
    private List<@Valid FormQuestionDTO> questions;
}
