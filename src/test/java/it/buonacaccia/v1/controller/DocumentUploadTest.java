package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import it.buonacaccia.model.Document;
import it.buonacaccia.model.DocumentStatus;
import it.buonacaccia.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US2 e US4 della 006: il socio carica il documento richiesto, il sistema controlla il file. */
class DocumentUploadTest extends EnrollmentTestSupport {

    static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    DocumentRepository documents;

    @BeforeEach
    void clearDocuments() {
        documents.deleteAll();
    }

    static byte[] file(byte[] head, int size) {
        byte[] content = new byte[size];
        System.arraycopy(head, 0, content, 0, head.length);
        return content;
    }

    /** Evento con la foto (JPEG o PNG fino a 4000 byte, online) e la liberatoria (cartacea). */
    String eventWithDocuments() {
        return publish(openEvent("obligations", List.of(
                Map.of("type", "DOCUMENT", "code", "FOTO", "description", "Foto tessera",
                        "allowedMimeTypes", List.of("image/jpeg", "image/png"), "maxSizeBytes", 4000),
                Map.of("type", "DOCUMENT", "code", "LIBERATORIA", "description", "Liberatoria firmata",
                        "delivery", "PAPER")))).get("id").asString();
    }

    static String upload(String enrollmentId, String code) {
        return ENROLLMENTS + "/" + enrollmentId + "/obligations/" + code + "/document";
    }

    static String id(JsonNode enrollment) {
        return enrollment.get("id").asString();
    }

    JsonNode obligationOf(String username, String enrollmentId, String code) {
        for (JsonNode o : enrollment(username, enrollmentId).get("obligations")) {
            if ("DOCUMENT".equals(o.get("type").asString()) && code.equals(o.get("code").asString())) {
                return o;
            }
        }
        throw new AssertionError("Adempimento " + code + " assente");
    }

    @Test
    void il_caricamento_valido_mette_l_adempimento_in_verifica() {
        JsonNode enrollment = enrolled("rover", eventWithDocuments());

        Result result = postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "foto.jpg", "image/jpeg", file(JPEG_HEAD, 3000));

        assertThat(result.status()).as("%s", result.body()).isEqualTo(201);
        JsonNode document = result.body().get("payload");
        assertThat(document.get("status").asString()).isEqualTo("UPLOADED");
        assertThat(document.get("contentType").asString()).isEqualTo("image/jpeg");
        assertThat(document.get("sizeBytes").asLong()).isEqualTo(3000);
        assertThat(document.get("sha256").asString()).hasSize(64);
        assertThat(obligationOf("rover", id(enrollment), "FOTO").get("status").asString()).isEqualTo("IN_REVIEW");
        assertThat(enrollment("rover", id(enrollment)).get("status").asString()).isEqualTo("REGISTERED");
    }

    @Test
    void il_presentatore_puo_caricare_per_il_ragazzo() {
        String eventId = publish(scoutEvent("obligations", List.of(
                Map.of("type", "DOCUMENT", "code", "FOTO", "description", "Foto")))).get("id").asString();
        Result enrolled = enrollFor("socio", "100006", eventId);
        assertThat(enrolled.status()).isEqualTo(201);
        Result result = postFile(upload(enrolled.body().get("payload").get("id").asString(), "FOTO"),
                tokenOf("socio"), "foto.png", "image/png", file(PNG_HEAD, 500));
        assertThat(result.status()).isEqualTo(201);
    }

    @Test
    void il_file_si_giudica_dai_byte_e_non_dal_nome() {
        JsonNode enrollment = enrolled("rover", eventWithDocuments());
        String path = upload(id(enrollment), "FOTO");
        String token = tokenOf("rover");
        byte[] executable = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00};

        Result disguised = postFile(path, token, "foto.jpg", "image/jpeg", executable);
        EventManagementTest.assertError(disguised, 422, 42200);
        assertThat(fields(disguised.body().get("payload"))).containsExactly("file");

        Result empty = postFile(path, token, "foto.jpg", "image/jpeg", new byte[0]);
        EventManagementTest.assertError(empty, 422, 42200);

        Result pdf = postFile(path, token, "foto.pdf", "application/pdf", "%PDF-1.7 prova".getBytes());
        EventManagementTest.assertError(pdf, 422, 42200);

        Result tooBig = postFile(path, token, "foto.jpg", "image/jpeg", file(JPEG_HEAD, 4001));
        EventManagementTest.assertError(tooBig, 422, 42200);

        assertThat(documents.count()).isZero();
        assertThat(obligationOf("rover", id(enrollment), "FOTO").get("status").asString()).isEqualTo("TODO");
    }

    @Test
    void oltre_il_limite_del_server_la_risposta_e_sempre_sul_campo_file() {
        JsonNode enrollment = enrolled("rover", eventWithDocuments());

        Result result = postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "foto.jpg", "image/jpeg",
                file(JPEG_HEAD, 11 * 1024 * 1024));

        EventManagementTest.assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).containsExactly("file");
    }

    @Test
    void un_nuovo_caricamento_sostituisce_il_precedente() {
        JsonNode enrollment = enrolled("rover", eventWithDocuments());
        String path = upload(id(enrollment), "FOTO");
        String token = tokenOf("rover");
        String first = postFile(path, token, "a.jpg", "image/jpeg", file(JPEG_HEAD, 100)).body().get("payload").get("id").asString();

        Result second = postFile(path, token, "b.png", "image/png", file(PNG_HEAD, 200));

        assertThat(second.status()).isEqualTo(201);
        assertThat(documents.findById(first)).get().extracting(Document::getStatus).isEqualTo(DocumentStatus.REPLACED);
        assertThat(documents.findAll()).filteredOn(d -> d.getStatus() == DocumentStatus.UPLOADED).hasSize(1);
    }

    @Test
    void cartaceo_estraneo_e_iscrizione_chiusa_non_si_caricano() {
        JsonNode enrollment = enrolled("rover", eventWithDocuments());
        byte[] content = file(JPEG_HEAD, 100);

        Result paper = postFile(upload(id(enrollment), "LIBERATORIA"), tokenOf("rover"), "l.jpg", "image/jpeg", content);
        EventManagementTest.assertError(paper, 409, 40910);

        Result unknown = postFile(upload(id(enrollment), "ALTRO"), tokenOf("rover"), "l.jpg", "image/jpeg", content);
        assertThat(unknown.status()).isEqualTo(404);

        Result stranger = postFile(upload(id(enrollment), "FOTO"), tokenOf("scolta"), "l.jpg", "image/jpeg", content);
        assertThat(stranger.status()).isEqualTo(404);
        Result secretariat = postFile(upload(id(enrollment), "FOTO"), tokenOf("segreteria"), "l.jpg", "image/jpeg", content);
        assertThat(secretariat.status()).isEqualTo(404);

        assertThat(post(ENROLLMENTS + "/" + id(enrollment) + "/withdraw", tokenOf("rover"), null).status()).isEqualTo(200);
        Result closed = postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "l.jpg", "image/jpeg", content);
        EventManagementTest.assertError(closed, 409, 40910);
        assertThat(documents.count()).isZero();
    }

    @Test
    void due_caricamenti_insieme_lasciano_un_solo_documento_in_verifica() {
        JsonNode enrollment = enrolled("rover", eventWithDocuments());
        String path = upload(id(enrollment), "FOTO");
        String token = tokenOf("rover");

        List<CompletableFuture<Result>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int n = i;
            calls.add(CompletableFuture.supplyAsync(() -> postFile(path, token, "f" + n + ".jpg", "image/jpeg", file(JPEG_HEAD, 100 + n))));
        }
        List<Result> results = calls.stream().map(CompletableFuture::join).toList();

        assertThat(results).allSatisfy(r -> assertThat(r.status()).isIn(201, 409));
        assertThat(results).filteredOn(r -> r.status() == 201).isNotEmpty();
        assertThat(documents.findAll()).filteredOn(d -> d.getStatus() == DocumentStatus.UPLOADED).hasSize(1);
        assertThat(obligationOf("rover", id(enrollment), "FOTO").get("status").asString()).isEqualTo("IN_REVIEW");
    }
}
