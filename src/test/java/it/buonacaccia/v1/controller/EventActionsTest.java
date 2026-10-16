package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventFixtures.validEvent;
import static it.buonacaccia.v1.controller.EventFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import it.buonacaccia.AbstractTest;
import it.buonacaccia.config.ClockConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Feature 010: l'evento dice da solo cosa può fare chi lo guarda, e l'elenco gestito si cerca e si ordina. */
class EventActionsTest extends AbstractTest {

    private static final String EVENTS = "/api/v1/events";

    private JsonNode create(String user, Map<String, Object> body) {
        return post(EVENTS, tokenOf(user), body).body().get("payload");
    }

    private static List<String> actions(JsonNode event) {
        return event.get("availableActions").valueStream().map(JsonNode::asString).toList();
    }

    private JsonNode action(String user, JsonNode event, String path, Object body) {
        return post(EVENTS + "/" + event.get("id").asString() + path, tokenOf(user), body).body().get("payload");
    }

    @Test
    void actionsFollowTheEventThroughItsLife() {
        clock.setInstant(LocalDateTime.parse("2030-03-05T10:00").atZone(ClockConfig.ROME).toInstant());
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));
        assertThat(actions(draft)).containsExactly("EDIT", "PUBLISH", "CANCEL");

        JsonNode open = action("segreteria", draft, "/publish", Map.of("version", draft.get("version").asLong()));
        assertThat(open.get("status").asString()).isEqualTo("REGISTRATION_OPEN");
        assertThat(actions(open)).containsExactly("EDIT", "CLOSE_REGISTRATIONS", "CANCEL");

        JsonNode closed = action("segreteria", open, "/close-registrations", Map.of("version", open.get("version").asLong()));
        assertThat(actions(closed)).containsExactly("EDIT", "CANCEL");

        JsonNode cancelled = action("segreteria", closed, "/cancel",
                Map.of("reason", "Maltempo", "version", closed.get("version").asLong()));
        assertThat(actions(cancelled)).isEmpty();
    }

    @Test
    void publishedBeforeOpeningCannotBeClosedYet() {
        clock.setInstant(LocalDateTime.parse("2030-02-01T10:00").atZone(ClockConfig.ROME).toInstant());
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));
        JsonNode published = action("segreteria", draft, "/publish", null);

        assertThat(published.get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(actions(published)).containsExactly("EDIT", "CANCEL");
    }

    @Test
    void anotherRegionAndThePublicHaveNoActions() {
        clock.setInstant(LocalDateTime.parse("2030-03-05T10:00").atZone(ClockConfig.ROME).toInstant());
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));
        JsonNode open = action("segreteria", draft, "/publish", null);
        String path = EVENTS + "/" + open.get("id").asString();

        assertThat(actions(get(path, tokenOf("segreteria-lig")).body().get("payload"))).isEmpty();
        assertThat(actions(get(path, tokenOf("socio")).body().get("payload"))).isEmpty();
        assertThat(actions(get(path).body().get("payload"))).isEmpty();
        assertThat(actions(get(path, tokenOf("segreteria")).body().get("payload"))).contains("CANCEL");
        assertThat(actions(get(path, tokenOf("amministratore")).body().get("payload"))).contains("CANCEL");

        JsonNode found = get(EVENTS + "?q=" + open.get("title").asString().replace(" ", "%20")).body()
                .get("payload").get("content").get(0);
        assertThat(actions(found)).isEmpty();
    }

    @Test
    void managedListSearchesByTitleCaseInsensitively() {
        JsonNode mine = create("segreteria", with(validEvent("PIE", "2030"), "title", "Zeta Cercami Alfa"));
        create("segreteria", with(validEvent("PIE", "2030"), "title", "Altro evento"));

        Result result = get(EVENTS + "/managed?q=cercami", tokenOf("segreteria"));

        assertThat(result.status()).isEqualTo(200);
        JsonNode content = result.body().get("payload").get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id").asString()).isEqualTo(mine.get("id").asString());
        assertThat(actions(content.get(0))).containsExactly("EDIT", "PUBLISH", "CANCEL");
    }

    @Test
    void managedListSortsByTitleAndRejectsUnknownSort() {
        create("segreteria", with(validEvent("PIE", "2030"), "title", "Ordine B"));
        create("segreteria", with(validEvent("PIE", "2030"), "title", "Ordine A"));
        create("segreteria", with(validEvent("PIE", "2030"), "title", "Ordine C"));

        List<String> titles = get(EVENTS + "/managed?q=Ordine&sort=title,desc", tokenOf("segreteria"))
                .body().get("payload").get("content").valueStream().map(e -> e.get("title").asString()).toList();
        assertThat(titles).containsExactly("Ordine C", "Ordine B", "Ordine A");

        Result bad = get(EVENTS + "/managed?sort=colore", tokenOf("segreteria"));
        assertThat(bad.status()).isEqualTo(422);
    }

    @Test
    void managedListShowsNewestFirstByDefault() {
        JsonNode first = create("segreteria", with(validEvent("PIE", "2030"), "title", "Primo creato"));
        JsonNode second = create("segreteria", with(validEvent("PIE", "2030"), "title", "Secondo creato"));

        List<String> ids = get(EVENTS + "/managed?size=100", tokenOf("segreteria"))
                .body().get("payload").get("content").valueStream().map(e -> e.get("id").asString()).toList();

        assertThat(ids.indexOf(second.get("id").asString())).isLessThan(ids.indexOf(first.get("id").asString()));
    }
}
