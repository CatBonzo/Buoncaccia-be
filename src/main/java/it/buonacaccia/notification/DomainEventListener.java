package it.buonacaccia.notification;

import it.buonacaccia.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumer Kafka degli eventi di dominio (FR-004 della 004). Legge il JSON e lo passa al dispatcher;
 * ritentativi e dead letter li gestisce il {@code DefaultErrorHandler} di {@link KafkaConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    private final NotificationDispatcher dispatcher;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaConfig.DOMAIN_EVENTS_TOPIC)
    public void onDomainEvent(String json) {
        DomainEvent event = jsonMapper.readValue(json, DomainEvent.class);
        log.debug("Ricevuto {} {}", event.type(), event.id());
        dispatcher.dispatch(event);
    }
}
