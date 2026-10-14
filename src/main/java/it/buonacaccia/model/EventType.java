package it.buonacaccia.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Tipo evento (FR-001): modello con requisiti e adempimenti predefiniti. Non si cancella, si disattiva. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("event_types")
public class EventType {

    /** Codice univoco, {@code ^[A-Z0-9_]{2,20}$}. */
    @Id
    private String code;

    private String name;
    private EventCategory category;
    private Requirements defaultRequirements;
    @Builder.Default
    private List<Obligation> defaultObligations = new ArrayList<>();
    /** Evento "a candidatura" (BR-12). */
    private boolean application;
    /** Disattivato = non usabile per nuovi eventi (FR-003). */
    @Builder.Default
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;
}
