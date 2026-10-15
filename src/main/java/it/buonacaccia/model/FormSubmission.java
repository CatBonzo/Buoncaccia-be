package it.buonacaccia.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Scheda di un'iscrizione (collezione `form_submissions`, una per iscrizione). Conserva la copia del modello
 * al primo salvataggio (FR-004). Livello e regione dell'evento servono alla regola territoriale.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("form_submissions")
public class FormSubmission {

    @Id
    private String id;

    @Indexed(unique = true)
    private String enrollmentId;
    private String eventId;
    private EventLevel eventLevel;
    private Region eventRegion;
    private String participantId;
    private String typeCode;

    private List<FormSection> sections;
    private Map<String, String> answers;
    private FormStatus status;
    private Instant updatedAt;
    private Instant submittedAt;
    private String reopenedBy;
    private Instant reopenedAt;
    private String reopenReason;

    @Version
    private Long version;
}
