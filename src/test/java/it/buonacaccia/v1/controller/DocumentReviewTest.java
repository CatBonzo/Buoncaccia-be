package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.DocumentUploadTest.JPEG_HEAD;
import static it.buonacaccia.v1.controller.DocumentUploadTest.file;
import static it.buonacaccia.v1.controller.DocumentUploadTest.id;
import static it.buonacaccia.v1.controller.DocumentUploadTest.upload;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import it.buonacaccia.MailpitClient.Mail;
import it.buonacaccia.service.EnrollmentJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US3 della 006: la segreteria valida o rifiuta il documento in verifica. */
class DocumentReviewTest extends EnrollmentTestSupport {

    private static final String ROVER = "rover@buonacaccia.local";

    @Autowired
    EnrollmentJobs jobs;

    /** Evento gratuito con un solo documento online (FOTO) e, se richiesto, una liberatoria cartacea. */
    private String event(boolean withPaper) {
        List<Map<String, Object>> obligations = withPaper
                ? List.of(Map.of("type", "DOCUMENT", "code", "FOTO", "description", "Foto tessera"),
                        Map.of("type", "DOCUMENT", "code", "LIBERATORIA", "description", "Liberatoria", "delivery", "PAPER"))
                : List.of(Map.of("type", "DOCUMENT", "code", "FOTO", "description", "Foto tessera"));
        return publish(openEvent("obligations", obligations, "fee", 0)).get("id").asString();
    }

    private String uploaded(JsonNode enrollment) {
        Result result = postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "foto.jpg", "image/jpeg", file(JPEG_HEAD, 200));
        assertThat(result.status()).as("%s", result.body()).isEqualTo(201);
        return result.body().get("payload").get("id").asString();
    }

    private static String validate(String documentId) {
        return "/api/v1/documents/" + documentId + "/validate";
    }

    private static String reject(String documentId) {
        return "/api/v1/documents/" + documentId + "/reject";
    }

    private JsonNode obligationFoto(String enrollmentId) {
        for (JsonNode o : enrollment("rover", enrollmentId).get("obligations")) {
            if ("FOTO".equals(o.get("code").asString())) {
                return o;
            }
        }
        throw new AssertionError("FOTO assente");
    }

    @Test
    void la_validazione_soddisfa_l_adempimento_e_completa_l_iscrizione() {
        JsonNode enrollment = enrolled("rover", event(false));
        String documentId = uploaded(enrollment);

        Result result = post(validate(documentId), tokenOf("segreteria"), null);

        assertThat(result.status()).as("%s", result.body()).isEqualTo(200);
        assertThat(result.body().get("payload").get("status").asString()).isEqualTo("VALIDATED");
        assertThat(result.body().get("payload").get("reviewedBy").isNull()).isFalse();
        assertThat(obligationFoto(id(enrollment)).get("status").asString()).isEqualTo("SATISFIED");
        assertThat(enrollment("rover", id(enrollment)).get("status").asString()).isEqualTo("COMPLETED");
        MAIL.expectOne(ROVER, "Iscrizione " + enrollment.get("number").asString() + " completata");
    }

    @Test
    void il_rifiuto_riapre_l_adempimento_e_avvisa_con_il_motivo() {
        JsonNode enrollment = enrolled("rover", event(false));
        String documentId = uploaded(enrollment);

        Result result = post(reject(documentId), tokenOf("segreteria"), Map.of("reason", "Foto sfocata"));

        assertThat(result.status()).as("%s", result.body()).isEqualTo(200);
        assertThat(result.body().get("payload").get("status").asString()).isEqualTo("REJECTED");
        assertThat(result.body().get("payload").get("rejectionReason").asString()).isEqualTo("Foto sfocata");
        JsonNode obligation = obligationFoto(id(enrollment));
        assertThat(obligation.get("status").asString()).isEqualTo("TODO");
        assertThat(obligation.get("note").asString()).isEqualTo("Foto sfocata");
        assertThat(enrollment("rover", id(enrollment)).get("status").asString()).isEqualTo("REGISTERED");
        Mail mail = MAIL.expectOne(ROVER, "Documento da ricaricare per l'iscrizione " + enrollment.get("number").asString());
        assertThat(mail.text()).contains("\"FOTO\"").contains("Motivo: Foto sfocata");

        // il socio ricarica e il documento torna in verifica
        assertThat(postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "nuova.jpg", "image/jpeg", file(JPEG_HEAD, 300)).status())
                .isEqualTo(201);
        assertThat(obligationFoto(id(enrollment)).get("status").asString()).isEqualTo("IN_REVIEW");
    }

    @Test
    void il_rifiuto_vuole_un_motivo() {
        JsonNode enrollment = enrolled("rover", event(false));
        String documentId = uploaded(enrollment);

        EventManagementTest.assertError(post(reject(documentId), tokenOf("segreteria"), Map.of("reason", " ")), 422, 42200);
        EventManagementTest.assertError(post(reject(documentId), tokenOf("segreteria"), null), 422, 42200);
        assertThat(obligationFoto(id(enrollment)).get("status").asString()).isEqualTo("IN_REVIEW");
    }

    @Test
    void solo_la_segreteria_della_regione_giusta_decide() {
        JsonNode enrollment = enrolled("rover", event(false));
        String documentId = uploaded(enrollment);

        EventManagementTest.assertError(post(validate(documentId), tokenOf("segreteria-lig"), null), 403, 40300);
        EventManagementTest.assertError(post(validate(documentId), tokenOf("socio"), null), 403, 40300);
        EventManagementTest.assertError(post(reject(documentId), tokenOf("rover"), Map.of("reason", "x")), 403, 40300);
        EventManagementTest.assertError(post("/api/v1/documents/inesistente/validate", tokenOf("segreteria"), null), 404, 40400);
        assertThat(obligationFoto(id(enrollment)).get("status").asString()).isEqualTo("IN_REVIEW");
    }

    @Test
    void un_documento_gia_deciso_o_sostituito_non_si_decide_piu() {
        JsonNode enrollment = enrolled("rover", event(false));
        String first = uploaded(enrollment);
        String second = uploaded(enrollment);

        EventManagementTest.assertError(post(validate(first), tokenOf("segreteria"), null), 409, 40960);
        assertThat(post(validate(second), tokenOf("segreteria"), null).status()).isEqualTo(200);
        EventManagementTest.assertError(post(validate(second), tokenOf("segreteria"), null), 409, 40960);
        EventManagementTest.assertError(post(reject(second), tokenOf("segreteria"), Map.of("reason", "tardi")), 409, 40960);
        // dopo la validazione il socio non può più caricare
        EventManagementTest.assertError(postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "x.jpg", "image/jpeg",
                file(JPEG_HEAD, 10)), 409, 40910);
    }

    @Test
    void validazione_e_rifiuto_insieme_ne_vince_uno_solo() {
        JsonNode enrollment = enrolled("rover", event(false));
        String documentId = uploaded(enrollment);

        CompletableFuture<Result> ok = CompletableFuture.supplyAsync(() -> post(validate(documentId), tokenOf("segreteria"), null));
        CompletableFuture<Result> no = CompletableFuture.supplyAsync(
                () -> post(reject(documentId), tokenOf("amministratore"), Map.of("reason", "No")));

        List<Integer> statuses = List.of(ok.join().status(), no.join().status());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String obligation = obligationFoto(id(enrollment)).get("status").asString();
        assertThat(obligation).isIn("SATISFIED", "TODO");
    }

    @Test
    void il_cartaceo_si_segna_ricevuto_per_codice_senza_toccare_gli_altri() {
        JsonNode enrollment = enrolled("rover", event(true));
        String path = ENROLLMENTS + "/" + id(enrollment) + "/obligations/DOCUMENT/satisfy?code=LIBERATORIA";

        assertThat(post(path, tokenOf("segreteria"), null).status()).isEqualTo(200);

        JsonNode after = enrollment("rover", id(enrollment));
        for (JsonNode o : after.get("obligations")) {
            if ("LIBERATORIA".equals(o.get("code").asString())) {
                assertThat(o.get("status").asString()).isEqualTo("SATISFIED");
            } else if ("FOTO".equals(o.get("code").asString())) {
                assertThat(o.get("status").asString()).isEqualTo("TODO");
            }
        }
        EventManagementTest.assertError(post(path, tokenOf("segreteria"), null), 409, 40910);
    }

    @Test
    void chi_aspetta_la_verifica_non_scade_e_non_riceve_promemoria() {
        clock.setInstant(Instant.now());
        JsonNode enrollment = enrolled("rover", event(false));
        String number = enrollment.get("number").asString();
        uploaded(enrollment);
        Instant deadline = Instant.parse(enrollment("rover", id(enrollment)).get("deadline").asString());

        clock.setInstant(deadline.minus(Duration.ofHours(12)));
        assertThat(jobs.sendReminders()).isZero();
        clock.setInstant(deadline.plus(Duration.ofDays(1)));
        assertThat(jobs.expireOverdue()).isZero();

        assertThat(enrollment("rover", id(enrollment)).get("status").asString()).isEqualTo("REGISTERED");
        MAIL.assertStays(ROVER, "Promemoria: l'iscrizione " + number, 0, Duration.ofSeconds(1));
    }

    @Test
    void dopo_un_rifiuto_il_socio_ha_almeno_tre_giorni_per_ricaricare() {
        clock.setInstant(Instant.now());
        JsonNode enrollment = enrolled("rover", event(false));
        String documentId = uploaded(enrollment);
        Instant deadline = Instant.parse(enrollment("rover", id(enrollment)).get("deadline").asString());
        clock.setInstant(deadline.plus(Duration.ofHours(1)));

        assertThat(post(reject(documentId), tokenOf("segreteria"), Map.of("reason", "Sfocata")).status()).isEqualTo(200);

        Instant extended = Instant.parse(enrollment("rover", id(enrollment)).get("deadline").asString());
        assertThat(extended).isAfterOrEqualTo(clock.instant().plus(Duration.ofDays(3)).minusSeconds(5));
        assertThat(jobs.expireOverdue()).isZero();
    }
}
