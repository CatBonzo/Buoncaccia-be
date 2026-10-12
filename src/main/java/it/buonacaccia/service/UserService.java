package it.buonacaccia.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.User;
import it.buonacaccia.model.UserStatus;
import it.buonacaccia.repository.nosql.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Utenti: upsert su agesciUUID e allineamento dal token (FR-001, FR-002, FR-011). */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CircuitBreakerService circuitBreakerService;

    /**
     * Crea o aggiorna un utente con chiave {@code agesciUUID} (FR-001, FR-002): se esiste già restano
     * il suo id, lo stato e la data di creazione, il resto viene da {@code candidate}.
     */
    public User upsertByAgesciUUID(User candidate) {
        User existing = findByAgesciUUID(candidate.agesciUUID());
        User user = existing == null ? candidate : new User(existing.id(), candidate.agesciUUID(),
                candidate.username(), candidate.email(), candidate.firstName(), candidate.lastName(),
                existing.status(), candidate.census(), candidate.roles(), candidate.entitlements(),
                existing.createdAt(), Instant.now());
        log.debug("Utente {} ({}): {}", user.username(), user.id(), existing != null ? "aggiornato" : "creato");
        return save(user);
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
        User existing = findByAgesciUUID(agesciUUID);
        if (existing == null) {
            log.info("Primo accesso di agesciUUID {}: utente creato dai claim del token", agesciUUID);
        }
        User user = Optional.ofNullable(existing)
                .map(found -> new User(found.id(), found.agesciUUID(), found.username(), found.email(),
                        found.firstName(), found.lastName(), found.status(), found.census(),
                        roles, entitlements, found.createdAt(), now))
                .orElseGet(() -> new User(UUID.randomUUID().toString(), agesciUUID,
                        jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"),
                        jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"),
                        UserStatus.ACTIVE, null, roles, entitlements, now, now));
        return save(user);
    }

    private User findByAgesciUUID(String agesciUUID) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB,
                () -> userRepository.findByAgesciUUID(agesciUUID).orElse(null));
    }

    private User save(User user) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, () -> userRepository.save(user));
    }

    private static List<String> listClaim(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? List.of() : values;
    }
}
