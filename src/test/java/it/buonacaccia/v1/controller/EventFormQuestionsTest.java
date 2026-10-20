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

    @Test
    void la_scheda_medica_richiesta_diventa_un_documento_da_caricare() {
        JsonNode event = publish(openEvent("medicalFormRequired", true));

        assertThat(event.get("medicalFormRequired").asBoolean()).isTrue();
        assertThat(event.get("obligations").valueStream())
                .anyMatch(o -> "DOCUMENT".equals(o.get("type").asString()) && "SCHEDA_MEDICA".equals(o.get("code").asString()));
        JsonNode enrollment = enrolled("rover", event.get("id").asString());
        assertThat(enrollment.get("obligations").valueStream().map(o -> String.valueOf(o.get("code").asString())))
                .contains("SCHEDA_MEDICA");
    }

    @Test
    void intolleranze_e_allergie_richieste_sono_domande_obbligatorie_e_riservate() {
        JsonNode event = publish(openEvent("dietaryInfoRequired", true));

        assertThat(hasFormObligation(event)).isTrue();
        JsonNode enrollment = enrolled("rover", event.get("id").asString());
        JsonNode questions = get(form(id(enrollment)), tokenOf("rover")).body().get("payload").get("sections").get(0).get("questions");
        assertThat(questions.valueStream().map(q -> q.get("id").asString())).containsExactly("intolleranze", "allergie");
        assertThat(questions.valueStream()).allMatch(q -> q.get("required").asBoolean() && q.get("sensitive").asBoolean());

        Result submit = post(form(id(enrollment)) + "/submit", tokenOf("rover"), null);
        EventManagementTest.assertError(submit, 422, 42200);
    }
}
