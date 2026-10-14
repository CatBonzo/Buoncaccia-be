package it.buonacaccia.notification;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import it.buonacaccia.config.KafkaConfig;
import it.buonacaccia.model.OutboxEvent;
import it.buonacaccia.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pubblica su Kafka gli eventi di dominio dell'outbox (research R1 della 004). A ogni giro prende
 * al più 100 eventi in ordine di scrittura e aspetta la conferma del broker per ciascuno; al primo
 * errore si ferma (Kafka è probabilmente fermo) e riprova al giro dopo: nessun evento si perde
 * (NFR-06). Lo stesso evento può partire due volte se l'applicazione cade dopo l'invio: il consumer
 * è idempotente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    private final AtomicBoolean paused = new AtomicBoolean();

    @Scheduled(fixedDelayString = "${buonacaccia.notifications.relay-delay:PT1S}")
    public void run() {
        if (!paused.get()) {
            publishPending();
        }
    }

    /** @return quanti eventi sono stati pubblicati in questo giro */
    public synchronized int publishPending() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);
        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(KafkaConfig.DOMAIN_EVENTS_TOPIC, event.getAggregateId(),
                        jsonMapper.writeValueAsString(event.getPayload())).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(e.getMessage());
                outboxRepository.save(event);
                log.warn("Kafka non disponibile, {} eventi restano in outbox: {}", pending.size() - published,
                        e.getMessage());
                return published;
            }
            event.setStatus(OutboxEvent.Status.PUBLISHED);
            event.setPublishedAt(clock.instant());
            outboxRepository.save(event);
            published++;
            log.debug("Pubblicato {} {}", event.getType(), event.getId());
        }
        return published;
    }

    /** Ferma il relay (prove su Kafka non disponibile, US6). */
    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }
}
