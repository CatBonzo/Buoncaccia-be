package it.buonacaccia.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** File allegato a un evento dalla segreteria, per esempio programma o regolamento (collezione {@code event_attachments}). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("event_attachments")
public class EventAttachment {

    @Id
    private String id;

    @Indexed
    private String eventId;
    private String name;
    private String contentType;
    private long sizeBytes;
    private String storageKey;
    private String uploadedBy;
    private Instant uploadedAt;
}
