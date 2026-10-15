package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.DocumentUploadTest.JPEG_HEAD;
import static it.buonacaccia.v1.controller.DocumentUploadTest.file;
import static it.buonacaccia.v1.controller.DocumentUploadTest.id;
import static it.buonacaccia.v1.controller.DocumentUploadTest.upload;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import it.buonacaccia.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US5 della 006: la coda dei documenti da verificare, per regione, evento e stato. */
class DocumentQueueTest extends EnrollmentTestSupport {

    private static final List<Map<String, Object>> FOTO =
            List.of(Map.of("type", "DOCUMENT", "code", "FOTO", "description", "Foto"));

    @Autowired
    DocumentRepository documents;

    @BeforeEach
    void clearDocuments() {
        documents.deleteAll();
    }

    private String uploadFor(String user, String eventId) {
        JsonNode enrollment = enrolled(user, eventId);
        Result result = postFile(upload(id(enrollment), "FOTO"), tokenOf(user), "f.jpg", "image/jpeg", file(JPEG_HEAD, 80));
        assertThat(result.status()).as("%s", result.body()).isEqualTo(201);
        return result.body().get("payload").get("id").asString();
    }

    private static List<String> ids(Result result) {
        return result.body().get("payload").get("content").valueStream().map(d -> d.get("id").asString()).toList();
    }

    @Test
    void la_coda_e_della_regione_e_parte_dal_piu_vecchio() {
        String pie = publish(openEvent("obligations", FOTO, "fee", 0)).get("id").asString();
        String lig = publish(openEvent("region", "LIG", "obligations", FOTO, "fee", 0)).get("id").asString();
        String first = uploadFor("rover", pie);
        String second = uploadFor("scolta", pie);
        String other = uploadFor("rover-lig", lig);

        assertThat(ids(get("/api/v1/documents", tokenOf("segreteria")))).containsExactly(first, second);
        assertThat(ids(get("/api/v1/documents", tokenOf("segreteria-lig")))).containsExactly(other);
        assertThat(ids(get("/api/v1/documents", tokenOf("amministratore")))).containsExactly(first, second, other);
        assertThat(ids(get("/api/v1/documents?eventId=" + pie + "&size=1&page=1", tokenOf("amministratore")))).containsExactly(second);
    }

    @Test
    void i_documenti_decisi_escono_dalla_coda_e_si_trovano_per_stato() {
        String pie = publish(openEvent("obligations", FOTO, "fee", 0)).get("id").asString();
        String first = uploadFor("rover", pie);
        String second = uploadFor("scolta", pie);
        assertThat(post("/api/v1/documents/" + first + "/validate", tokenOf("segreteria"), null).status()).isEqualTo(200);

        assertThat(ids(get("/api/v1/documents", tokenOf("segreteria")))).containsExactly(second);
        assertThat(ids(get("/api/v1/documents?status=VALIDATED", tokenOf("segreteria")))).containsExactly(first);
    }

    @Test
    void la_coda_e_solo_per_la_segreteria_e_i_parametri_si_controllano() {
        EventManagementTest.assertError(get("/api/v1/documents", tokenOf("socio")), 403, 40300);
        EventManagementTest.assertError(get("/api/v1/documents?page=-1", tokenOf("segreteria")), 422, 42200);
        EventManagementTest.assertError(get("/api/v1/documents?status=BOH", tokenOf("segreteria")), 422, 42200);
        assertThat(get("/api/v1/documents", null).status()).isEqualTo(401);
    }
}
