package it.buonacaccia.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * La persona che usa il sistema (collezione {@code users}). {@code census} null = non socio AGESCI.
 * {@code roles} ed {@code entitlements} sono la copia dell'ultimo token: non servono ad autorizzare.
 */
@Document("users")
public record User(
        @Id String id,
        @Indexed(unique = true) String agesciUUID,
        String username,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Census census,
        List<String> roles,
        List<String> entitlements,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isMember() {
        return census != null;
    }
}
