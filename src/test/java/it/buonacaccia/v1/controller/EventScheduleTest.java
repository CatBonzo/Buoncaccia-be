package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventFixtures.validEvent;
import static it.buonacaccia.v1.controller.EventFixtures.version;
import static it.buonacaccia.v1.controller.EventFixtures.with;
import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import it.buonacaccia.AbstractTest;
import it.buonacaccia.config.ClockConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US3: apertura e chiusura delle iscrizioni seguono l'ora italiana, senza job (FR-013…FR-015). */
class EventScheduleTest extends AbstractTest {

    private static final String EVENTS = "/api/v1/events";

    @Test
    void statusFollowsTheClockInDetailAndSearch() {
        String tag = "S" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode event = publishedEvent(with(validEvent("PIE", "2034"), "title", tag + " Route"));
        String path = EVENTS + "/" + event.get("id").asString();

        assertStatus(path, "2034-03-01T08:59:59", "PUBLISHED");
        assertStatus(path, "2034-03-01T09:00:00", "REGISTRATION_OPEN");
        assertThat(get(EVENTS + "?q=" + tag + "&status=REGISTRATION_OPEN").body().get("payload").get("totalElements").asLong()).isEqualTo(1);
        assertThat(get(EVENTS + "?q=" + tag + "&availability=OPEN").body().get("payload").get("totalElements").asLong()).isEqualTo(1);
        assertStatus(path, "2034-06-30T23:59:59", "REGISTRATION_OPEN");
        assertStatus(path, "2034-07-01T00:00:00", "REGISTRATION_CLOSED");
        assertThat(get(EVENTS + "?q=" + tag + "&status=REGISTRATION_CLOSED").body().get("payload").get("totalElements").asLong()).isEqualTo(1);
        assertStatus(path, "2034-07-20T00:00:00", "IN_PROGRESS");
        assertThat(get(EVENTS + "?q=" + tag + "&status=IN_PROGRESS").body().get("payload").get("totalElements").asLong()).isEqualTo(1);
        assertStatus(path, "2034-07-28T00:00:00", "COMPLETED");
        assertThat(get(EVENTS + "?q=" + tag + "&status=COMPLETED").body().get("payload").get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void segreteriaClosesRegistrationsEarly() {
        JsonNode event = publishedEvent(validEvent("PIE", "2034"));
        String path = EVENTS + "/" + event.get("id").asString();
        setRome("2034-03-10T10:00");

        assertError(post(path + "/close-registrations", tokenOf("segreteria-lig"), null), 403, 40300);
        Result closed = post(path + "/close-registrations", tokenOf("segreteria"), version(event.get("version").asLong()));

        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.body().get("payload").get("status").asString()).isEqualTo("REGISTRATION_CLOSED");
        assertThat(closed.body().get("payload").get("availability").asString()).isEqualTo("CLOSED");
        assertError(post(path + "/close-registrations", tokenOf("segreteria"), null), 409, 40901);
    }

    @Test
    void cannotCloseBeforeOpening() {
        JsonNode event = publishedEvent(validEvent("PIE", "2034"));
        setRome("2034-02-01T10:00");

        assertError(post(EVENTS + "/" + event.get("id").asString() + "/close-registrations", tokenOf("segreteria"), null), 409, 40901);
    }

    private JsonNode publishedEvent(Map<String, Object> body) {
        JsonNode draft = post(EVENTS, tokenOf("segreteria"), body).body().get("payload");
        return post(EVENTS + "/" + draft.get("id").asString() + "/publish", tokenOf("segreteria"), null).body().get("payload");
    }

    private void assertStatus(String path, String romeTime, String expected) {
        setRome(romeTime);
        assertThat(get(path).body().get("payload").get("status").asString()).as(romeTime).isEqualTo(expected);
    }

    private void setRome(String localDateTime) {
        clock.setInstant(LocalDateTime.parse(localDateTime).atZone(ClockConfig.ROME).toInstant());
    }
}
