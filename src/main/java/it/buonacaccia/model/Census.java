package it.buonacaccia.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.Indexed;

/** Dati di censimento AGESCI, sotto-documento dell'utente; assente per chi non è socio. */
public record Census(
        @Indexed(unique = true, sparse = true) String memberCode,
        String groupId,
        String unitId,
        Branch branch,
        MemberType memberType,
        CensusStatus status,
        Integer year,
        Instant lastSyncAt) {
}
