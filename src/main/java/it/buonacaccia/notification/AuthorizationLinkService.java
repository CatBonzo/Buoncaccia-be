package it.buonacaccia.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;

import it.buonacaccia.config.NotificationProperties;
import it.buonacaccia.model.AuthorizationLink;
import it.buonacaccia.repository.AuthorizationLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Link monouso per le autorizzazioni dei capi (FR-015…FR-019 della 004, research R8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthorizationLinkRepository linkRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    /**
     * Crea il link per un capo e restituisce l'indirizzo da mettere nell'e-mail. Se lo stesso evento
     * arriva di nuovo (l'invio precedente è fallito) il link esistente riceve un token nuovo: del vecchio
     * c'è solo l'hash, e l'e-mail che lo conteneva non è partita.
     */
    public String create(DomainEvent event, String leaderId) {
        String token = newToken();
        AuthorizationLink link = linkRepository.findByDomainEventIdAndLeaderId(event.id(), leaderId)
                .orElseGet(() -> AuthorizationLink.builder()
                        .domainEventId(event.id())
                        .leaderId(leaderId)
                        .enrollmentId(event.enrollment().id())
                        .obligationType(event.obligationType())
                        .createdAt(clock.instant())
                        .build());
        link.setTokenHash(hash(token));
        linkRepository.save(link);
        log.debug("Link di autorizzazione {} per il capo {} sull'iscrizione {}", link.getObligationType(), leaderId,
                link.getEnrollmentId());
        return properties.linkBaseUrl() + "/" + token;
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
