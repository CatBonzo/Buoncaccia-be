package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import it.buonacaccia.config.KafkaConfig;
import it.buonacaccia.model.Notification;
import it.buonacaccia.model.NotificationStatus;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.OutboxEvent;
import it.buonacaccia.notification.OutboxRelay;
import it.buonacaccia.repository.NotificationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import tools.jackson.databind.JsonNode;

/** US6 della 004: nessuna e-mail persa, nessuna doppia; registro consultabile (FR-002, FR-004…FR-007). */
class NotificationReliabilityTest extends EnrollmentTestSupport {

    private static final String ROVER = "rover@buonacaccia.local";
    /** Porta su cui nessuno ascolta: l'invio fallisce subito con "connection refused". */
    private static final int CLOSED_PORT = 1;

    @Autowired
    OutboxRelay relay;

    @Autowired
    JavaMailSenderImpl mailSender;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void conKafkaFermoLeIscrizioniRiesconoELeConfermePartonoAllaRipresa() {
        List<String> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            events.add(publishOpen());
        }
        Set<String> numbers = new HashSet<>();
        relay.pause();
        try {
            for (String eventId : events) {
                numbers.add(enrolled("rover", eventId).get("number").asString());
            }
            await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                    .until(() -> countConfirmations(numbers) == 0);
        } finally {
            relay.resume();
        }

        await().atMost(Duration.ofSeconds(30)).until(() -> countConfirmations(numbers) == 20);
        for (String number : numbers) {
            assertThat(MAIL.find(ROVER, number)).as(number).hasSize(1);
        }
    }

    @Test
    void conIlServerDiPostaFermoPerPocoLEmailPartePoiUnaVolta() {
        int port = mailSender.getPort();
        mailSender.setPort(CLOSED_PORT);
        String id;
        String number;
        try {
            JsonNode enrollment = enrolled("rover", publishOpen());
            id = enrollment.get("id").asString();
            number = enrollment.get("number").asString();
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                    .until(() -> confirmation(id).filter(n -> n.getAttempts() >= 2).isPresent());
        } finally {
            mailSender.setPort(port);
        }

        MAIL.expectOne(ROVER, number);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Notification notification = confirmation(id).orElseThrow();
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getAttempts()).isGreaterThanOrEqualTo(3);
        });
    }

    @Test
    void esauritiITentativiLAvvisoEFallitoEFinisceNelDeadLetter() {
        int port = mailSender.getPort();
        mailSender.setPort(CLOSED_PORT);
        String id;
        try {
            id = enrolled("rover", publishOpen()).get("id").asString();
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(confirmation(id)).hasValueSatisfying(n -> {
                        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
                        assertThat(n.getAttempts()).isEqualTo(4);
                        assertThat(n.getLastError()).isNotBlank();
                    }));
        } finally {
            mailSender.setPort(port);
        }
        String domainEventId = confirmation(id).orElseThrow().getDomainEventId();
        assertThat(deadLetterContains(domainEventId)).isTrue();
    }

    @Test
    void loStessoEventoConsegnatoDueVolteMandaUnaSolaEmail() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        String number = enrollment.get("number").asString();
        MAIL.expectOne(ROVER, number);
        String domainEventId = confirmation(enrollment.get("id").asString()).orElseThrow().getDomainEventId();

        // Come se il relay fosse caduto dopo l'invio a Kafka e prima di segnarlo: lo ripubblica.
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(domainEventId)),
                new Update().set("status", OutboxEvent.Status.PENDING), OutboxEvent.class);
        await().atMost(Duration.ofSeconds(10)).until(() ->
                mongoTemplate.findById(domainEventId, OutboxEvent.class).getStatus() == OutboxEvent.Status.PUBLISHED);

        MAIL.assertStays(ROVER, number, 1, Duration.ofSeconds(3));
        assertThat(notificationRepository.findByEnrollmentIdOrderByCreatedAtAsc(enrollment.get("id").asString()))
                .filteredOn(n -> n.getTemplate() == NotificationTemplate.ENROLLMENT_CONFIRMATION).hasSize(1);
    }

    @Test
    void laSegreteriaConsultaIlRegistroDegliAvvisi() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        String id = enrollment.get("id").asString();
        MAIL.expectOne(ROVER, enrollment.get("number").asString());
        MAIL.expectOne("capogruppo@buonacaccia.local", enrollment.get("number").asString());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Result result = get(ENROLLMENTS + "/" + id + "/notifications", tokenOf("segreteria"));
            assertThat(result.status()).as("%s", result.body()).isEqualTo(200);
            List<Map<String, String>> log = new ArrayList<>();
            for (JsonNode n : result.body().get("payload")) {
                log.add(Map.of("template", n.get("template").asString(), "role", n.get("recipientRole").asString(),
                        "status", n.get("status").asString()));
            }
            assertThat(log).contains(
                    Map.of("template", "ENROLLMENT_CONFIRMATION", "role", "PARTICIPANT", "status", "SENT"),
                    Map.of("template", "AUTHORIZATION_REQUEST", "role", "UNIT_LEADER", "status", "SENT"));
        });
        assertThat(get(ENROLLMENTS + "/" + id + "/notifications", tokenOf("rover")).status()).isEqualTo(403);
        assertThat(get(ENROLLMENTS + "/" + id + "/notifications", tokenOf("segreteria-lig")).status()).isEqualTo(404);
    }

    private long countConfirmations(Set<String> numbers) {
        return MAIL.find(ROVER, "registrata").stream()
                .filter(m -> numbers.stream().anyMatch(m.subject()::contains))
                .count();
    }

    private Optional<Notification> confirmation(String enrollmentId) {
        return notificationRepository.findByEnrollmentIdOrderByCreatedAtAsc(enrollmentId).stream()
                .filter(n -> n.getTemplate() == NotificationTemplate.ENROLLMENT_CONFIRMATION)
                .findFirst();
    }

    private static boolean deadLetterContains(String domainEventId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-dlt-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaConfig.DEAD_LETTER_TOPIC));
            long until = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < until) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value().contains(domainEventId)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
