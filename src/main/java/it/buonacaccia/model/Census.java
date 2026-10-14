package it.buonacaccia.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;

/** Dati di censimento AGESCI, sotto-documento dell'utente; assente per chi non è socio. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class Census {

    /** Codice socio, univoco. */
    @Indexed(unique = true, sparse = true)
    private String memberCode;

    private String groupId;
    private String unitId;
    private Branch branch;
    private MemberType memberType;
    private CensusStatus status;
    private Integer year;
    /** Capo gruppo del proprio {@code groupId}: autorizza le iscrizioni dei soci del gruppo. */
    private boolean groupLeader;
    private Instant lastSyncAt;
}
