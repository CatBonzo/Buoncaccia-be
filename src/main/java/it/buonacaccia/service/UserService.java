package it.buonacaccia.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.User;
import it.buonacaccia.model.UserStatus;
import it.buonacaccia.repository.UserRepository;
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
        User existing = findByAgesciUUID(candidate.getAgesciUUID());
        if (existing == null) {
            log.debug("Utente {} ({}): creato", candidate.getUsername(), candidate.getId());
            return save(candidate);
        }
        existing.setUsername(candidate.getUsername());
        existing.setEmail(candidate.getEmail());
        existing.setFirstName(candidate.getFirstName());
        existing.setLastName(candidate.getLastName());
        existing.setBirthDate(candidate.getBirthDate());
        existing.setCensus(candidate.getCensus());
        existing.setRoles(candidate.getRoles());
        existing.setEntitlements(candidate.getEntitlements());
        existing.setUpdatedAt(Instant.now());
        log.debug("Utente {} ({}): aggiornato", existing.getUsername(), existing.getId());
        return save(existing);
    }

    /**
     * Copia ruoli ed entitlement del token sull'utente; se la persona non esiste ancora la crea dai
     * claim (FR-011). L'autorizzazione usa sempre il token, non questa copia.
     */
    public User syncFromToken(Jwt jwt) {
        Instant now = Instant.now();
        String agesciUUID = jwt.getClaimAsString("agesciUUID");
        User user = findByAgesciUUID(agesciUUID);
        if (user == null) {
            log.info("Primo accesso di agesciUUID {}: utente creato dai claim del token", agesciUUID);
            user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .agesciUUID(agesciUUID)
                    .username(jwt.getClaimAsString("preferred_username"))
                    .email(jwt.getClaimAsString("email"))
                    .firstName(jwt.getClaimAsString("given_name"))
                    .lastName(jwt.getClaimAsString("family_name"))
                    .status(UserStatus.ACTIVE)
                    .createdAt(now)
                    .build();
        }
        user.setRoles(listClaim(jwt, "role"));
        user.setEntitlements(listClaim(jwt, "entitlement"));
        user.setUpdatedAt(now);
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
