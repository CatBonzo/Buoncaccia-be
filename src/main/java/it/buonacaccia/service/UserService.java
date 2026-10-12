package it.buonacaccia.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import it.buonacaccia.config.MockedIdentityProperties.CensusData;
import it.buonacaccia.config.MockedIdentityProperties.MockedUser;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.User;
import it.buonacaccia.model.UserStatus;
import it.buonacaccia.repository.nosql.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Utenti: creazione dai dati di prova e allineamento dal token (FR-001, FR-002, FR-011). */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Crea o aggiorna l'utente di una voce di {@code mocked-identity.yml}, con chiave {@code agesciUUID}:
     * l'id resta quello già salvato, altrimenti quello indicato nella voce o un UUID nuovo.
     */
    public User upsertMockUser(MockedUser entry) {
        Instant now = Instant.now();
        User existing = userRepository.findByAgesciUUID(entry.agesciUUID()).orElse(null);
        String id = existing != null ? existing.id()
                : entry.buonaCacciaUUID() != null ? entry.buonaCacciaUUID() : UUID.randomUUID().toString();
        return userRepository.save(new User(
                id,
                entry.agesciUUID(),
                entry.username(),
                entry.email(),
                entry.firstName(),
                entry.lastName(),
                existing != null ? existing.status() : UserStatus.ACTIVE,
                toCensus(entry.census()),
                entry.authentication().role(),
                entry.authentication().entitlement(),
                existing != null ? existing.createdAt() : now,
                now));
    }

    /**
     * Copia ruoli ed entitlement del token sull'utente; se la persona non esiste ancora la crea dai
     * claim (FR-011). L'autorizzazione usa sempre il token, non questa copia.
     */
    public User syncFromToken(Jwt jwt) {
        Instant now = Instant.now();
        String agesciUUID = jwt.getClaimAsString("agesciUUID");
        List<String> roles = listClaim(jwt, "role");
        List<String> entitlements = listClaim(jwt, "entitlement");
        User user = userRepository.findByAgesciUUID(agesciUUID)
                .map(existing -> new User(existing.id(), existing.agesciUUID(), existing.username(), existing.email(),
                        existing.firstName(), existing.lastName(), existing.status(), existing.census(),
                        roles, entitlements, existing.createdAt(), now))
                .orElseGet(() -> new User(UUID.randomUUID().toString(), agesciUUID,
                        jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"),
                        jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"),
                        UserStatus.ACTIVE, null, roles, entitlements, now, now));
        return userRepository.save(user);
    }

    private static List<String> listClaim(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? List.of() : values;
    }

    private static Census toCensus(CensusData data) {
        if (data == null) {
            return null;
        }
        return new Census(data.memberCode(), data.groupId(), data.unitId(), data.branch(), data.memberType(),
                data.status(), data.year(), null);
    }
}
