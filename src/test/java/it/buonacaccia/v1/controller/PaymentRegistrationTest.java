package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import it.buonacaccia.MailpitClient.Mail;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US2: la segreteria registra i pagamenti (FR-005…FR-009 della 005). */
class PaymentRegistrationTest extends EnrollmentTestSupport {

    private static final String ROVER = "rover@buonacaccia.local";

    /** Iscrizione del rover con tutti gli adempimenti tranne il pagamento già soddisfatti. */
    private JsonNode enrolledAndReady() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        for (JsonNode obligation : enrollment.get("obligations")) {
            String type = obligation.get("type").asString();
            if (!"PAYMENT".equals(type) && "TODO".equals(obligation.get("status").asString())) {
                assertThat(post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/obligations/" + type + "/satisfy",
                        tokenOf("segreteria"), null).status()).isEqualTo(200);
            }
        }
        return enrollment(("rover"), enrollment.get("id").asString());
    }

    private Result pay(String user, JsonNode enrollment, Object amount, String reference) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("amount", amount);
        body.put("method", "BANK_TRANSFER");
        body.put("receivedOn", today().toString());
        body.put("reference", reference);
        return post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/payments", user == null ? null : tokenOf(user), body);
    }

    private static String payment(JsonNode enrollment, String type) {
        return obligation(enrollment, type).get("status").asString();
    }

    @Test
    void ilPagamentoPienoSoddisfaLAdempimentoCompletaLIscrizioneEAvvisaIlSocio() {
        JsonNode ready = enrolledAndReady();
        assertThat(ready.get("status").asString()).isEqualTo("REGISTERED");

        Result paid = pay("segreteria", ready, 150, "CRO-1");

        assertThat(paid.status()).as("%s", paid.body()).isEqualTo(201);
        assertThat(paid.body().get("payload").get("amount").asDouble()).isEqualTo(150.0);
        assertThat(paid.body().get("payload").get("status").asString()).isEqualTo("VALID");
        JsonNode after = enrollment("rover", ready.get("id").asString());
        assertThat(after.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(payment(after, "PAYMENT")).isEqualTo("SATISFIED");
        assertThat(after.get("payment").get("paid").asDouble()).isEqualTo(150.0);
        assertThat(after.get("payment").get("due").asDouble()).isZero();
        assertThat(after.get("paymentInfo").isNull()).isTrue();
        Mail mail = MAIL.expectOne(ROVER, "Pagamento ricevuto per l'iscrizione " + ready.get("number").asString());
        assertThat(mail.text()).contains("150,00 €").contains("L'iscrizione è completa");
    }

    @Test
    void ilPagamentoParzialeEPoiIlResto() {
        JsonNode ready = enrolledAndReady();

        assertThat(pay("segreteria", ready, 50, "CRO-A").status()).isEqualTo(201);
        JsonNode partial = enrollment("rover", ready.get("id").asString());
        assertThat(partial.get("status").asString()).isEqualTo("REGISTERED");
        assertThat(payment(partial, "PAYMENT")).isEqualTo("TODO");
        assertThat(partial.get("payment").get("due").asDouble()).isEqualTo(100.0);
        assertThat(partial.get("paymentInfo").isNull()).isFalse();
        Mail first = MAIL.expectOne(ROVER, "Pagamento ricevuto per l'iscrizione " + ready.get("number").asString());
        assertThat(first.text()).contains("Resta da pagare: 100,00 €");

        assertThat(pay("segreteria", ready, "100.00", "CRO-B").status()).isEqualTo(201);
        assertThat(enrollment("rover", ready.get("id").asString()).get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    void oltreIlResiduoOConPiuDiDueDecimaliEUnErroreSulCampoImporto() {
        JsonNode ready = enrolledAndReady();

        Result over = pay("segreteria", ready, 160, "X");
        assertError(over, 422, 42200);
        assertThat(EventFixtures.fields(over.body().get("payload"))).contains("amount");
        assertError(pay("segreteria", ready, "10.005", "X"), 422, 42200);
        assertError(pay("segreteria", ready, 0, "X"), 422, 42200);
        assertError(pay("segreteria", ready, -5, "X"), 422, 42200);
        assertThat(enrollment("rover", ready.get("id").asString()).get("payment").get("paid").asDouble()).isZero();
    }

    @Test
    void laDataDiIncassoNonPuoEssereNelFuturo() {
        JsonNode ready = enrolledAndReady();
        Result result = post(ENROLLMENTS + "/" + ready.get("id").asString() + "/payments", tokenOf("segreteria"),
                Map.of("amount", 10, "receivedOn", today().plusDays(1).toString()));

        assertError(result, 422, 42200);
        assertThat(EventFixtures.fields(result.body().get("payload"))).contains("receivedOn");
    }

    @Test
    void ilDoppioneConStessoImportoERiferimentoEUnConflitto() {
        JsonNode ready = enrolledAndReady();
        assertThat(pay("segreteria", ready, 50, "CRO-1").status()).isEqualTo(201);

        assertError(pay("segreteria", ready, 50, "cro-1"), 409, 40930);
        assertThat(pay("segreteria", ready, 50, "CRO-2").status()).isEqualTo(201);
        assertThat(pay("segreteria", ready, 10, null).status()).isEqualTo(201);
        assertThat(pay("segreteria", ready, 10, null).status()).isEqualTo(201);
    }

    @Test
    void inAttesaOChiusaNonSiRegistraNulla() {
        String eventId = publishOpen("seats", 1);
        JsonNode seated = enrolled("rover", eventId);
        JsonNode waiting = enrolled("scolta", eventId);

        assertError(pay("segreteria", waiting, 50, "W"), 409, 40910);

        assertThat(post(ENROLLMENTS + "/" + seated.get("id").asString() + "/withdraw", tokenOf("rover"), null).status())
                .isEqualTo(200);
        assertError(pay("segreteria", seated, 50, "C"), 409, 40910);
    }

    @Test
    void soloChiGestisceLEventoPuoRegistrare() {
        JsonNode ready = enrolledAndReady();

        assertError(pay("segreteria-lig", ready, 50, "L"), 403, 40300);
        assertError(pay("rover", ready, 50, "R"), 403, 40300);
        assertError(pay(null, ready, 50, "N"), 401, 40100);
        assertThat(pay("amministratore", ready, 50, "A").status()).isEqualTo(201);
    }

    @Test
    void annullareUnPagamentoRiapreLAdempimentoEDaUnaScadenzaNuova() {
        JsonNode ready = enrolledAndReady();
        String paymentId = pay("segreteria", ready, 150, "SBAGLIATO").body().get("payload").get("id").asString();
        JsonNode completed = enrollment("rover", ready.get("id").asString());
        assertThat(completed.get("status").asString()).isEqualTo("COMPLETED");
        Instant oldDeadline = Instant.parse(completed.get("deadline").asString());
        clock.setInstant(romeTime(today().plusDays(2), 10, 0));

        assertError(post("/api/v1/payments/" + paymentId + "/cancel", tokenOf("segreteria"), Map.of()), 422, 42200);
        Result cancelled = post("/api/v1/payments/" + paymentId + "/cancel", tokenOf("segreteria"),
                Map.of("reason", "Importo registrato sull'iscrizione sbagliata"));

        assertThat(cancelled.status()).as("%s", cancelled.body()).isEqualTo(200);
        assertThat(cancelled.body().get("payload").get("status").asString()).isEqualTo("CANCELLED");
        JsonNode after = enrollment("rover", ready.get("id").asString());
        assertThat(after.get("status").asString()).isEqualTo("REGISTERED");
        assertThat(payment(after, "PAYMENT")).isEqualTo("TODO");
        assertThat(after.get("payment").get("paid").asDouble()).isZero();
        assertThat(Instant.parse(after.get("deadline").asString())).isAfter(oldDeadline);
        assertError(post("/api/v1/payments/" + paymentId + "/cancel", tokenOf("segreteria"), Map.of("reason", "Ancora")), 409, 40931);
        assertError(post("/api/v1/payments/" + paymentId + "/cancel", tokenOf("segreteria-lig"), Map.of("reason", "x")), 403, 40300);
        JsonNode list = get(ENROLLMENTS + "/" + ready.get("id").asString() + "/payments", tokenOf("segreteria")).body().get("payload");
        assertThat(list.get(0).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(list.get(0).get("cancelReason").asString()).contains("sbagliata");
    }

    @Test
    void dueRegistrazioniContemporaneeDelSaldoRiesceSoloUna() throws Exception {
        JsonNode ready = enrolledAndReady();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Result>> tasks = new ArrayList<>();
            tasks.add(() -> pay("segreteria", ready, 150, "PAR-1"));
            tasks.add(() -> pay("segreteria", ready, 150, "PAR-2"));
            List<Integer> statuses = new ArrayList<>();
            for (Future<Result> future : pool.invokeAll(tasks)) {
                statuses.add(future.get().status());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
        JsonNode after = enrollment("rover", ready.get("id").asString());
        assertThat(after.get("payment").get("paid").asDecimal()).isEqualByComparingTo(new BigDecimal("150"));
    }

    @Test
    void ElencoVisibileAPartecipantePresentatoreESegreteria() {
        JsonNode ready = enrolledAndReady();
        pay("segreteria", ready, 50, "V");
        String path = ENROLLMENTS + "/" + ready.get("id").asString() + "/payments";

        assertThat(get(path, tokenOf("rover")).body().get("payload").size()).isEqualTo(1);
        assertThat(get(path, tokenOf("segreteria")).body().get("payload").size()).isEqualTo(1);
        assertError(get(path, tokenOf("scolta")), 404, 40400);
        assertError(get(path, tokenOf("segreteria-lig")), 404, 40400);
    }
}
