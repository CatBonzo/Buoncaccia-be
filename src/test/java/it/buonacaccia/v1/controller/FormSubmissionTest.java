package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.FormTemplateTest.question;
import static it.buonacaccia.v1.controller.FormTemplateTest.template;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US2 della 007: il partecipante compila, salva in bozza e invia. */
class FormSubmissionTest extends FormTestBase {

    private static final String ROVER = "rover@buonacaccia.local";

    @Test
    void la_scheda_mai_aperta_mostra_il_modello_senza_risposte() {
        JsonNode enrollment = enrolled("rover", formEvent());

        Result result = get(form(id(enrollment)), tokenOf("rover"));

        assertThat(result.status()).as("%s", result.body()).isEqualTo(200);
        JsonNode payload = result.body().get("payload");
        assertThat(payload.get("status").asString()).isEqualTo("DRAFT");
        assertThat(payload.get("sections").get(0).get("questions")).hasSize(4);
        assertThat(payload.get("answers")).isEmpty();
    }

    @Test
    void la_bozza_si_salva_piu_volte_e_l_ultima_sostituisce() {
        JsonNode enrollment = enrolled("rover", formEvent());

        assertThat(save("rover", id(enrollment), Map.of("fede", "prima", "anni", "3")).status()).isEqualTo(200);
        Result second = save("rover", id(enrollment), Map.of("fede", "seconda", "ok", "false"));

        assertThat(second.status()).isEqualTo(200);
        JsonNode answers = get(form(id(enrollment)), tokenOf("rover")).body().get("payload").get("answers");
        assertThat(answers.get("fede").asString()).isEqualTo("seconda");
        assertThat(answers.get("ok").asString()).isEqualTo("false");
        assertThat(answers.has("anni")).isFalse();
        assertThat(formObligation("rover", id(enrollment)).get("status").asString()).isEqualTo("TODO");
    }

    @Test
    void risposte_di_tipo_sbagliato_o_a_domande_sconosciute_sono_rifiutate() {
        JsonNode enrollment = enrolled("rover", formEvent());

        Result result = save("rover", id(enrollment), Map.of("ok", "forse", "inventata", "x"));

        EventManagementTest.assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).containsExactlyInAnyOrder("answers.ok", "answers.inventata");
    }

    @Test
    void l_invio_vuole_le_obbligatorie_e_poi_soddisfa_e_completa() {
        JsonNode enrollment = enrolled("rover", formEvent());
        save("rover", id(enrollment), Map.of("fede", "Credo nel cammino"));

        Result incomplete = submit("rover", id(enrollment));
        EventManagementTest.assertError(incomplete, 422, 42200);
        assertThat(fields(incomplete.body().get("payload"))).containsExactly("answers.ok");

        save("rover", id(enrollment), COMPLETE);
        Result done = submit("rover", id(enrollment));

        assertThat(done.status()).as("%s", done.body()).isEqualTo(200);
        assertThat(done.body().get("payload").get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(done.body().get("payload").get("submittedAt").isNull()).isFalse();
        assertThat(formObligation("rover", id(enrollment)).get("status").asString()).isEqualTo("SATISFIED");
        assertThat(enrollment("rover", id(enrollment)).get("status").asString()).isEqualTo("COMPLETED");
        MAIL.expectOne(ROVER, "Iscrizione " + enrollment.get("number").asString() + " completata");
    }

    @Test
    void dopo_l_invio_la_scheda_non_si_modifica_ne_si_reinvia() {
        JsonNode enrollment = enrolled("rover", formEvent());
        save("rover", id(enrollment), COMPLETE);
        assertThat(submit("rover", id(enrollment)).status()).isEqualTo(200);

        EventManagementTest.assertError(save("rover", id(enrollment), COMPLETE), 409, 40970);
        EventManagementTest.assertError(submit("rover", id(enrollment)), 409, 40970);
    }

    @Test
    void con_l_iscrizione_chiusa_non_si_compila() {
        JsonNode enrollment = enrolled("rover", formEvent());
        assertThat(post(ENROLLMENTS + "/" + id(enrollment) + "/withdraw", tokenOf("rover"), null).status()).isEqualTo(200);

        EventManagementTest.assertError(save("rover", id(enrollment), COMPLETE), 409, 40910);
        EventManagementTest.assertError(submit("rover", id(enrollment)), 409, 40910);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chi_ha_cominciato_conserva_il_modello_di_quando_ha_cominciato() {
        JsonNode enrollment = enrolled("rover", formEvent());
        save("rover", id(enrollment), Map.of("fede", "x"));

        put("/api/v1/event-types/ROSS/form-template", tokenOf("amministratore"), template(question("nuova", "TEXT", false)));

        JsonNode questions = get(form(id(enrollment)), tokenOf("rover")).body().get("payload").get("sections").get(0).get("questions");
        assertThat(questions).hasSize(4);
        JsonNode other = enrolled("scolta", formEvent());
        assertThat(get(form(id(other)), tokenOf("scolta")).body().get("payload").get("sections").get(0).get("questions")).hasSize(1);
    }

    @Test
    void due_invii_insieme_ne_riesce_uno_solo() {
        JsonNode enrollment = enrolled("rover", formEvent());
        save("rover", id(enrollment), COMPLETE);

        CompletableFuture<Result> a = CompletableFuture.supplyAsync(() -> submit("rover", id(enrollment)));
        CompletableFuture<Result> b = CompletableFuture.supplyAsync(() -> submit("rover", id(enrollment)));

        assertThat(List.of(a.join().status(), b.join().status())).containsExactlyInAnyOrder(200, 409);
        assertThat(formObligation("rover", id(enrollment)).get("status").asString()).isEqualTo("SATISFIED");
    }

    @Test
    void un_iscrizione_senza_scheda_non_ha_il_percorso() {
        JsonNode enrollment = enrolled("rover", publishOpen("fee", 0));

        assertThat(get(form(id(enrollment)), tokenOf("rover")).status()).isEqualTo(404);
    }
}
