package it.buonacaccia.model;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Modello di scheda di un tipo evento (collezione `form_templates`, id = codice del tipo, feature 007). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("form_templates")
public class FormTemplate {

    @Id
    private String typeCode;

    private List<FormSection> sections;
    private String updatedBy;
    private Instant updatedAt;
}
