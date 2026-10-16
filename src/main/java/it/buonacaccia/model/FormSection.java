package it.buonacaccia.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Sezione del modello di scheda (cammino scout, fede, servizio, informazioni sanitarie…). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FormSection {

    private String title;
    private List<FormQuestion> questions;
}
