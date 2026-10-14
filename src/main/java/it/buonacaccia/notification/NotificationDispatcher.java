package it.buonacaccia.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import it.buonacaccia.config.NotificationProperties;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.Notification;
import it.buonacaccia.model.NotificationStatus;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.RecipientRole;
import it.buonacaccia.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Trasforma un evento di dominio in e-mail (FR-004…FR-006 della 004, research R4). Per ogni
 * destinatario prima registra la notifica con chiave unica, poi invia: se lo stesso evento arriva due
 * volte, le notifiche già chiuse si saltano. Se un invio fallisce prova comunque gli altri destinatari
 * e poi rilancia l'errore, così Spring Kafka ritenta il messaggio (research R5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final RecipientResolver recipientResolver;
    private final MailComposer mailComposer;
    private final AuthorizationLinkService linkService;
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final Clock clock;

    public void dispatch(DomainEvent event) {
        List<Recipient> recipients = recipientResolver.resolve(event);
        if (recipients.isEmpty() && event.type() == DomainEventType.AUTHORIZATION_REQUESTED) {
            recordNoRecipient(event);
            return;
        }
        RuntimeException failure = null;
        for (Recipient recipient : recipients) {
            try {
                deliver(event, recipient);
            } catch (RuntimeException e) {
                log.warn("Invio {} a {} non riuscito: {}", recipient.template(), recipient.userId(), e.getMessage());
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Segna {@code FAILED} gli avvisi dell'evento ancora aperti, dopo l'ultimo tentativo (research R5). */
    public void markFailed(String domainEventId, String error) {
        for (Notification notification : notificationRepository.findByDomainEventIdAndStatus(domainEventId,
                NotificationStatus.PENDING)) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(error);
            notification.setUpdatedAt(clock.instant());
            notificationRepository.save(notification);
            log.error("Avviso {} a {} fallito dopo {} tentativi: {}", notification.getTemplate(),
                    notification.getRecipientId(), notification.getAttempts(), error);
        }
    }

    private void deliver(DomainEvent event, Recipient recipient) {
        Notification notification = claim(event, recipient);
        if (notification.getStatus().isDone()) {
            log.debug("Avviso {} a {} già chiuso ({}): salto", recipient.template(), recipient.userId(),
                    notification.getStatus());
            return;
        }
        String email = recipient.user().getEmail();
        Instant now = clock.instant();
        if (email == null || email.isBlank()) {
            notification.setStatus(NotificationStatus.NO_ADDRESS);
            notification.setUpdatedAt(now);
            notificationRepository.save(notification);
            log.info("Avviso {} per {}: nessun indirizzo e-mail", recipient.template(), recipient.userId());
            return;
        }
        String link = recipient.template() == NotificationTemplate.AUTHORIZATION_REQUEST
                ? linkService.create(event, recipient.userId()) : null;
        MailComposer.Mail mail = mailComposer.compose(event, recipient, link);
        notification.setEmail(email);
        notification.setSubject(mail.subject());
        notification.setAttempts(notification.getAttempts() + 1);
        notification.setUpdatedAt(now);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.from());
            message.setTo(email);
            message.setSubject(mail.subject());
            message.setText(mail.text());
            mailSender.send(message);
        } catch (RuntimeException e) {
            notification.setLastError(e.getMessage());
            notificationRepository.save(notification);
            throw e;
        }
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(clock.instant());
        notification.setLastError(null);
        notificationRepository.save(notification);
        log.info("E-mail {} inviata a {} ({})", recipient.template(), email, mail.subject());
    }

    /** La notifica dell'evento per il destinatario: nuova in {@code PENDING} o quella già registrata. */
    private Notification claim(DomainEvent event, Recipient recipient) {
        Instant now = clock.instant();
        Notification fresh = Notification.builder()
                .domainEventId(event.id())
                .domainEventType(event.type())
                .template(recipient.template())
                .recipientId(recipient.userId())
                .recipientRole(recipient.role())
                .enrollmentId(recipient.enrollmentId())
                .eventId(event.event() == null ? null : event.event().id())
                .status(NotificationStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            return notificationRepository.insert(fresh);
        } catch (DuplicateKeyException e) {
            return notificationRepository.findByDomainEventIdAndRecipientIdAndTemplate(event.id(),
                    recipient.userId(), recipient.template()).orElseThrow(() -> e);
        }
    }

    private void recordNoRecipient(DomainEvent event) {
        RecipientRole role = event.obligationType() == ObligationType.GROUP_LEADER_AUTHORIZATION
                ? RecipientRole.GROUP_LEADER : RecipientRole.UNIT_LEADER;
        Notification notification = claim(event, new Recipient(null, role, NotificationTemplate.AUTHORIZATION_REQUEST,
                event.enrollment().id()));
        if (notification.getStatus() == NotificationStatus.PENDING) {
            notification.setStatus(NotificationStatus.NO_RECIPIENT);
            notificationRepository.save(notification);
            log.info("Autorizzazione {} per l'iscrizione {}: nessun capo trovato", event.obligationType(),
                    event.enrollment().number());
        }
    }
}
