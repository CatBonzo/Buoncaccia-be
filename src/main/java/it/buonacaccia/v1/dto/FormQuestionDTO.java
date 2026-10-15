package it.buonacaccia.v1.dto;

import java.util.List;

import it.buonacaccia.model.QuestionType;
import jakarta.validation.constraints.NotNull;
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
public class FormQuestionDTO {

    @Size(max = 40, message = "Al massimo 40 caratteri.")
    private String id;
    @Size(max = 500, message = "Al massimo 500 caratteri.")
    private String text;
    @NotNull(message = "Indica il tipo della domanda.")
    private QuestionType type;
    private List<@Size(max = 200, message = "Al massimo 200 caratteri.") String> options;
    private boolean required;
    private boolean sensitive;
}
