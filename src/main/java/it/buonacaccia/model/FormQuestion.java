package it.buonacaccia.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Domanda del modello di scheda; `sensitive` per le informazioni sanitarie (feature 007, FR-010). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FormQuestion {

    private String id;
    private String text;
    private QuestionType type;
    private List<String> options;
    private boolean required;
    private boolean sensitive;
}
