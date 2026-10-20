package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.FormTemplateTest.question;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Feature 015: le domande della scheda le scrive la segreteria per ogni evento, e sono facoltative. */
class EventFormQuestionsTest extends FormTestBase {

    private static List<Map<String, Object>> ownQuestions() {
        return List.of(Map.of("title", "Domande dell'evento", "questions",
                List.of(question("hobby", "TEXT", false), question("meta", "LONG_TEXT", true))));
    }

    private static boolean hasFormObligation(JsonNode event) {
        return event.get("obligations").valueStream().anyMatch(o -> "ELECTRONIC_FORM".equals(o.get("type").asString()));
    }

    @Test
    void le_domande_dell_evento_creano_la_scheda_e_vincono_sul_modello_del_tipo() {
        JsonNode event = publish(openEvent("formSections", ownQuestions()));

        assertThat(hasFormObligation(event)).isTrue();
        assertThat(event.get("formSections").get(0).get("questions")).hasSize(2);
        JsonNode enrollment = enrolled("rover", event.get("id").asString());

        JsonNode form = get(form(id(enrollment)), tokenOf("rover")).body().get("payload");
        assertThat(form.get("sections").get(0).get("title").asString()).isEqualTo("Domande dell'evento");
        assertThat(form.get("sections").get(0).get("questions")).hasSize(2);
    }

    @Test
    void senza_domande_l_evento_non_ha_scheda() {
        JsonNode event = publish(openEvent());

        assertThat(hasFormObligation(event)).isFalse();
    }

    @Test
    void una_domanda_con_identificativo_sbagliato_e_rifiutata() {
        Map<String, Object> body = openEvent("formSections",
                List.of(Map.of("title", "S", "questions", List.of(question("Id Sbagliato", "TEXT", false)))));

        Result result = post(EVENTS, tokenOf("segreteria"), body);

        EventManagementTest.assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).anyMatch(f -> f.startsWith("formSections"));
    }

    @Test
    void dopo_la_pubblicazione_le_domande_non_si_cambiano() {
        Map<String, Object> body = openEvent("formSections", ownQuestions());
        JsonNode event = publish(body);

        Result changed = updateEvent(event, body, "formSections",
                List.of(Map.of("title", "Altre", "questions", List.of(question("nuova", "TEXT", false)))));

        assertThat(changed.status()).as("%s", changed.body()).isEqualTo(409);
    }
}
