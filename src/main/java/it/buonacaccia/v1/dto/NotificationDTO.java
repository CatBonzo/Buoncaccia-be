package it.buonacaccia.v1.dto;

import java.time.Instant;

import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.NotificationStatus;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.RecipientRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Un avviso del registro delle notifiche (FR-006, FR-007 della 004). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class NotificationDTO {

    private String id;
    private NotificationTemplate template;
    private DomainEventType domainEventType;
    private String recipientId;
    private RecipientRole recipientRole;
    private String email;
    private String subject;
    private NotificationStatus status;
    private int attempts;
    private String lastError;
    private Instant createdAt;
    private Instant sentAt;
}
