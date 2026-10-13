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
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * La persona che usa il sistema (collezione {@code users}). {@code census} null = non socio AGESCI.
 * {@code roles} ed {@code entitlements} sono la copia dell'ultimo token: non servono ad autorizzare.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("users")
public class User {

    /** Identificativo Buona Caccia (buonaCacciaUUID). */
    @Id
    private String id;

    @Indexed(unique = true)
    private String agesciUUID;

    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private Census census;

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Builder.Default
    private List<String> entitlements = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    /** Socio AGESCI = ha i dati di censimento (FR-012). */
    public boolean isMember() {
        return census != null;
    }
}
