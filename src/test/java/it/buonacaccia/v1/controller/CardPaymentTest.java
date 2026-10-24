package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Feature 018: il socio paga con la carta (simulata) tutto il residuo. */
class CardPaymentTest extends EnrollmentTestSupport {

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
        return enrollment("rover", enrollment.get("id").asString());
    }

    private Result payByCard(String user, JsonNode enrollment, Object last4) {
        return post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/card-payment", tokenOf(user), Map.of("cardLast4", last4));
    }

    @Test
    void ilSocioPagaTuttoIlResiduoConLaCartaEL_IscrizioneSiCompleta() {
        JsonNode ready = enrolledAndReady();

        Result paid = payByCard("rover", ready, "4242");

        assertThat(paid.status()).as("%s", paid.body()).isEqualTo(201);
        JsonNode payload = paid.body().get("payload");
        assertThat(payload.get("method").asString()).isEqualTo("CARD");
        assertThat(payload.get("amount").asDouble()).isEqualTo(150.0);
        assertThat(payload.get("reference").asString()).isEqualTo("Carta •••• 4242");
        JsonNode after = enrollment("rover", ready.get("id").asString());
        assertThat(after.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(after.get("payment").get("due").asDouble()).isZero();
    }

    @Test
    void sePagatoGiaOPrimaDellAutorizzazioneIlBackendRispondeConflitto() {
        JsonNode enrolledOnly = enrolled("rover", publishOpen());
        assertError(payByCard("rover", enrolledOnly, "4242"), 409, 40910);

        JsonNode ready = enrolledAndReady();
        assertThat(payByCard("rover", ready, "4242").status()).isEqualTo(201);
        assertError(payByCard("rover", ready, "4242"), 409, 40910);
    }

    @Test
    void soloIlSuoSocioPuoPagareEIDatiDellaCartaSonoControllati() {
        JsonNode ready = enrolledAndReady();

        assertError(payByCard("scolta", ready, "4242"), 404, 40400);
        assertError(payByCard("rover", ready, "42"), 422, 42200);
        assertError(payByCard("rover", ready, "4242 4242 4242 4242"), 422, 42200);
        assertThat(enrollment("rover", ready.get("id").asString()).get("payment").get("paid").asDouble()).isZero();
    }

    @Test
    void laSegreteriaNonRegistraPagamentiConCartaESenzaTokenNonSiPaga() {
        JsonNode ready = enrolledAndReady();
        Result bySecretariat = post(ENROLLMENTS + "/" + ready.get("id").asString() + "/payments", tokenOf("segreteria"),
                Map.of("amount", 10, "method", "CARD", "receivedOn", today().toString()));

        assertError(bySecretariat, 422, 42200);
        assertThat(post(ENROLLMENTS + "/" + ready.get("id").asString() + "/card-payment", null, Map.of("cardLast4", "4242")).status())
                .isEqualTo(401);
    }
}
