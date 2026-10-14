package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.buonacaccia.AbstractTest;
import it.buonacaccia.model.Enrollment;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Base dei test sulle iscrizioni: eventi aperti con date relative a oggi (ora italiana) e senza i
 * vincoli "uno per tipo" e "massimo per gruppo", salvo i test che li provano. Prima di ogni test le
 * iscrizioni vengono cancellate, così gli utenti di prova si possono riusare.
 */
abstract class EnrollmentTestSupport extends AbstractTest {

    static final String EVENTS = "/api/v1/events";
    static final String ENROLLMENTS = "/api/v1/enrollments";

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clearEnrollments() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), Enrollment.class);
    }

    static LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Rome"));
    }

    /** Requisiti R/S senza vincoli fra eventi (età 16–21, branca RS, ragazzi). */
    static Map<String, Object> rsRequirements() {
        Map<String, Object> requirements = new HashMap<>();
        requirements.put("minAge", 16);
        requirements.put("maxAge", 21);
        requirements.put("branches", List.of("RS"));
        requirements.put("memberTypes", List.of("YOUTH"));
        requirements.put("oneActivePerType", false);
        return requirements;
    }

    /** Evento ROSS piemontese con iscrizioni aperte da ieri, partenza fra 40 giorni. */
    static Map<String, Object> openEvent(Object... overrides) {
        Map<String, Object> event = new HashMap<>(validEvent("PIE", String.valueOf(today().getYear())));
        event.put("registrationOpenDate", today().minusDays(1).toString());
        event.put("registrationCloseDate", today().plusDays(20).toString());
        event.put("startDate", today().plusDays(40).toString());
        event.put("endDate", today().plusDays(45).toString());
        event.put("requirements", rsRequirements());
        return EventFixtures.with(event, overrides);
    }

    /** Crea e pubblica l'evento con la segreteria della sua regione; restituisce l'evento pubblicato. */
    JsonNode publish(Map<String, Object> event) {
        String token = "LIG".equals(event.get("region")) ? tokenOf("segreteria-lig") : tokenOf("segreteria");
        Result created = post(EVENTS, token, event);
        assertThat(created.status()).as("creazione: %s", created.body()).isEqualTo(201);
        Result published = post(EVENTS + "/" + created.body().get("payload").get("id").asString() + "/publish", token, null);
        assertThat(published.status()).as("pubblicazione: %s", published.body()).isEqualTo(200);
        return published.body().get("payload");
    }

    String publishOpen(Object... overrides) {
        return publish(openEvent(overrides)).get("id").asString();
    }

    Result enroll(String username, String eventId) {
        return post(EVENTS + "/" + eventId + "/enrollments", tokenOf(username), null);
    }

    Result enrollFor(String leader, String memberCode, String eventId) {
        return post(EVENTS + "/" + eventId + "/enrollments", tokenOf(leader), Map.of("memberCode", memberCode));
    }

    /** Iscrizione riuscita: il payload. */
    JsonNode enrolled(String username, String eventId) {
        Result result = enroll(username, eventId);
        assertThat(result.status()).as("iscrizione: %s", result.body()).isEqualTo(201);
        return result.body().get("payload");
    }

    Result getEnrollment(String username, String id) {
        return get(ENROLLMENTS + "/" + id, tokenOf(username));
    }

    static List<String> fields(JsonNode payload) {
        return EventFixtures.fields(payload);
    }

    static JsonNode obligation(JsonNode enrollment, String type) {
        for (JsonNode obligation : enrollment.get("obligations")) {
            if (type.equals(obligation.get("type").asString())) {
                return obligation;
            }
        }
        return null;
    }
}
