package it.buonacaccia.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.Base64;
import java.util.HexFormat;

import it.buonacaccia.config.NotificationProperties;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.model.AuthorizationLink;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.User;
import it.buonacaccia.repository.AuthorizationLinkRepository;
import it.buonacaccia.repository.EnrollmentRepository;
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.repository.UserRepository;
import it.buonacaccia.service.EnrollmentService;
import it.buonacaccia.v1.dto.AuthorizationLinkDTO;
import it.buonacaccia.v1.dto.LinkOutcomeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Link monouso per le autorizzazioni dei capi (FR-015…FR-019 della 004, research R8). Leggere il link
 * non cambia nulla; autorizzare o negare lo "consuma" con un aggiornamento condizionato su
 * {@code usedAt: null} (un solo uso anche con due clic insieme) e poi applica la decisione come dal sito.
 * Ogni link non valido riceve la stessa risposta, senza dire nulla dell'iscrizione (FR-018).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthorizationLinkRepository linkRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EnrollmentService enrollmentService;
    private final MongoTemplate mongoTemplate;
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

    /** Iscrizione e domanda, senza cambiare nulla (FR-016). */
    public AuthorizationLinkDTO read(String token) {
        AuthorizationLink link = validLink(token);
        Enrollment enrollment = enrollmentRepository.findById(link.getEnrollmentId()).orElseThrow(AuthorizationLinkService::notValid);
        Event event = eventRepository.findById(enrollment.getEventId()).orElseThrow(AuthorizationLinkService::notValid);
        return AuthorizationLinkDTO.builder()
                .enrollmentNumber(enrollment.getNumber())
                .eventCode(event.getCode())
                .eventTitle(event.getTitle())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .location(event.getLocation())
                .participantName(enrollment.getParticipantName())
                .presenterName(enrollment.getPresenterName())
                .obligationType(link.getObligationType())
                .leaderName(userRepository.findById(link.getLeaderId()).map(AuthorizationLinkService::fullName).orElse(null))
                .deadline(enrollment.getDeadline())
                .build();
    }

    public LinkOutcomeDTO approve(String token) {
        AuthorizationLink link = use(token, AuthorizationLink.Decision.APPROVED);
        Enrollment enrollment = decide(() -> enrollmentService.approveAsLeader(link.getEnrollmentId(),
                link.getObligationType(), link.getLeaderId()));
        return outcome(enrollment, AuthorizationLink.Decision.APPROVED);
    }

    public LinkOutcomeDTO reject(String token, String reason) {
        AuthorizationLink link = use(token, AuthorizationLink.Decision.REJECTED);
        Enrollment enrollment = decide(() -> enrollmentService.rejectAsLeader(link.getEnrollmentId(),
                link.getObligationType(), link.getLeaderId(), reason));
        return outcome(enrollment, AuthorizationLink.Decision.REJECTED);
    }

    /** Valido se non usato, iscrizione attiva, autorizzazione ancora da dare e scadenza non passata (FR-017). */
    static boolean isValid(AuthorizationLink link, Enrollment enrollment, Instant now) {
        return link.getUsedAt() == null
                && enrollment != null
                && enrollment.isActive()
                && enrollment.getDeadline() != null && now.isBefore(enrollment.getDeadline())
                && enrollment.getObligations().stream().anyMatch(o -> o.getType() == link.getObligationType()
                        && o.getStatus() == ObligationStatus.TODO);
    }

    private AuthorizationLink validLink(String token) {
        AuthorizationLink link = linkRepository.findByTokenHash(hash(Objects.requireNonNullElse(token, "")))
                .orElseThrow(AuthorizationLinkService::notValid);
        Enrollment enrollment = enrollmentRepository.findById(link.getEnrollmentId()).orElse(null);
        if (!isValid(link, enrollment, clock.instant())) {
            throw notValid();
        }
        return link;
    }

    /** Consuma il link: solo il primo che arriva lo trova ancora libero. */
    private AuthorizationLink use(String token, AuthorizationLink.Decision decision) {
        AuthorizationLink link = validLink(token);
        AuthorizationLink used = mongoTemplate.findAndModify(
                new Query(Criteria.where("id").is(link.getId()).and("usedAt").is(null)),
                new Update().set("usedAt", clock.instant()).set("decision", decision),
                FindAndModifyOptions.options().returnNew(true), AuthorizationLink.class);
        if (used == null) {
            throw notValid();
        }
        return used;
    }

    /** Il link era valido ma nel frattempo l'autorizzazione non è più da dare (es. un altro capo l'ha appena data). */
    private static Enrollment decide(Supplier<Enrollment> action) {
        try {
            return action.get();
        } catch (BuonaCacciaException e) {
            if (e.errorCode().httpStatus() == 409) {
                throw new BuonaCacciaException(ErrorCode.DECISION_NOT_POSSIBLE);
            }
            throw e;
        }
    }

    private static LinkOutcomeDTO outcome(Enrollment enrollment, AuthorizationLink.Decision decision) {
        return LinkOutcomeDTO.builder()
                .enrollmentNumber(enrollment.getNumber())
                .decision(decision)
                .enrollmentStatus(enrollment.getStatus())
                .build();
    }

    private static String fullName(User user) {
        return (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
    }

    private static BuonaCacciaException notValid() {
        return new BuonaCacciaException(ErrorCode.LINK_NOT_VALID);
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
