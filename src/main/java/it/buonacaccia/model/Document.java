package it.buonacaccia.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * Documento caricato per un adempimento di tipo DOCUMENT (collezione {@code documents}, feature 006).
 * Il contenuto sta nello storage ({@code storageKey}); qui solo i metadati e l'esito della verifica.
 * Livello e regione dell'evento sono copiati per la regola territoriale e per la coda.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@org.springframework.data.mongodb.core.mapping.Document("documents")
@CompoundIndex(name = "enrollment_code_status", def = "{'enrollmentId': 1, 'code': 1, 'status': 1}")
public class Document {

    @Id
    private String id;

    private String enrollmentId;
    private String enrollmentNumber;

    @Indexed
    private String eventId;
    private String eventCode;
    private EventLevel eventLevel;
    private Region eventRegion;
    private String participantId;

    /** Codice del documento richiesto dall'evento (es. FOTO). */
    private String code;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private String storageKey;

    @Indexed
    private DocumentStatus status;

    private String uploadedBy;
    private Instant uploadedAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;

    @org.springframework.data.annotation.Version
    private Long version;
}
