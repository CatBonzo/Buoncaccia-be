package it.buonacaccia.config;

import it.buonacaccia.notification.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka per gli eventi di dominio (ADR-003, feature 004): un topic, il suo dead letter e i ritentativi
 * con attese crescenti (research R3, R5). Chiave e valore sono stringhe (JSON scritto dall'applicazione).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class KafkaConfig {

    public static final String DOMAIN_EVENTS_TOPIC = "buonacaccia.domain-events";
    public static final String DEAD_LETTER_TOPIC = DOMAIN_EVENTS_TOPIC + ".DLT";
    private static final int PARTITIONS = 3;

    @Bean
    NewTopic domainEventsTopic() {
        return TopicBuilder.name(DOMAIN_EVENTS_TOPIC).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic deadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Esauriti i tentativi: le notifiche ancora aperte diventano {@code FAILED} e il messaggio va nel
     * dead letter, dove resta da esaminare. Un JSON illeggibile va subito nel dead letter.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                          NotificationDispatcher dispatcher, JsonMapper jsonMapper,
                                          NotificationProperties properties) {
        DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (consumerRecord, e) -> new TopicPartition(DEAD_LETTER_TOPIC, consumerRecord.partition()));
        NotificationProperties.Retry retry = properties.retry();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.maxAttempts() - 1);
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        DefaultErrorHandler handler = new DefaultErrorHandler((consumerRecord, e) -> {
            String json = String.valueOf(consumerRecord.value());
            try {
                String id = jsonMapper.readTree(json).path("id").asString(null);
                if (id != null) {
                    dispatcher.markFailed(id, rootMessage(e));
                }
            } catch (JacksonException parse) {
                log.error("Messaggio illeggibile sul topic {}: {}", consumerRecord.topic(), parse.getMessage());
            }
            log.error("Evento di dominio nel dead letter {} dopo {} tentativi: {}", DEAD_LETTER_TOPIC,
                    retry.maxAttempts(), rootMessage(e));
            deadLetter.accept(consumerRecord, e);
        }, backOff);
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
