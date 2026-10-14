package it.buonacaccia.model;

import java.time.Instant;

import it.buonacaccia.notification.DomainEvent;
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
 * Evento di dominio in attesa di Kafka (collezione {@code outbox}, research R1 della 004). Si scrive
 * nella stessa transazione del cambio di stato; il relay lo pubblica e lo segna {@code PUBLISHED}.
 * Quelli pubblicati spariscono dopo 7 giorni (indice TTL).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("outbox")
@CompoundIndex(name = "status_created", def = "{'status': 1, 'createdAt': 1}")
public class OutboxEvent {

    public enum Status { PENDING, PUBLISHED }

    /** Uguale all'id del {@link DomainEvent}. */
    @Id
    private String id;

    private DomainEventType type;
    /** Id dell'iscrizione (o dell'evento): chiave del messaggio Kafka, tiene in ordine i fatti della stessa iscrizione. */
    private String aggregateId;
    private DomainEvent payload;
    private Status status;
    private int attempts;
    private String lastError;
    private Instant createdAt;

    @Indexed(name = "published_ttl", expireAfter = "7d")
    private Instant publishedAt;
}
