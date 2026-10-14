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
 * Link monouso con cui un capo autorizza o nega senza login (collezione {@code authorization_links},
 * FR-015…FR-019 della 004, research R8). Del token si salva solo l'hash SHA-256: chi legge il database
 * non può usare i link. La validità dipende dallo stato dell'iscrizione e si calcola a ogni lettura.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("authorization_links")
@CompoundIndex(name = "event_leader", def = "{'domainEventId': 1, 'leaderId': 1}", unique = true)
public class AuthorizationLink {

    public enum Decision { APPROVED, REJECTED }

    @Id
    private String id;

    @Indexed(unique = true)
    @ToString.Exclude
    private String tokenHash;

    @Indexed
    private String enrollmentId;
    private ObligationType obligationType;
    private String leaderId;
    private String domainEventId;

    private Instant createdAt;
    private Instant usedAt;
    private Decision decision;
}
