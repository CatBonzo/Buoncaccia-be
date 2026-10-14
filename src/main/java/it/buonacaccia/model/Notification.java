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
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un avviso a un destinatario per un evento di dominio (collezione {@code notifications}, FR-006
 * della 004). La chiave unica (evento, destinatario, modello) rende idempotente il consumer: lo
 * stesso evento consegnato due volte non manda due e-mail (research R4).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("notifications")
@CompoundIndex(name = "event_recipient_template", def = "{'domainEventId': 1, 'recipientId': 1, 'template': 1}",
        unique = true)
@CompoundIndex(name = "enrollment_created", def = "{'enrollmentId': 1, 'createdAt': 1}")
public class Notification {

    /** Destinatario di una notifica {@code NO_RECIPIENT}. */
    public static final String NOBODY = "-";

    @Id
    private String id;

    private String domainEventId;
    private DomainEventType domainEventType;
    private NotificationTemplate template;

    private String recipientId;
    private RecipientRole recipientRole;
    private String email;
    private String subject;

    private String enrollmentId;
    private String eventId;

    @Indexed
    private NotificationStatus status;
    private int attempts;
    private String lastError;

    private Instant createdAt;
    private Instant sentAt;
    private Instant updatedAt;
}
