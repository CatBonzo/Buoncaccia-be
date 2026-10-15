package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import it.buonacaccia.model.RegionSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US1: quanto devo pagare e a chi (FR-001…FR-004 della 005). */
class PaymentInfoTest extends EnrollmentTestSupport {

    private static final String IBAN = "IT60X0542811101000000123456";

    @BeforeEach
    void clearSettings() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), RegionSettings.class);
    }

    private void setSettings(String user, String key, String holder) {
        assertThat(put("/api/v1/region-settings/" + key, tokenOf(user),
                Map.of("holder", holder, "iban", IBAN, "contactEmail", "info@example.org", "hours", "Lun-Ven 9-13")).status())
                .isEqualTo(200);
    }

    private JsonNode authorized(String user, String eventId) {
        JsonNode enrollment = enrolled(user, eventId);
        assertThat(post(ENROLLMENTS + "/" + enrollment.get("id").asString()
                + "/obligations/UNIT_LEADER_AUTHORIZATION/satisfy", tokenOf("amministratore"), null).status()).isEqualTo(200);
        return enrollment(user, enrollment.get("id").asString());
    }

    @Test
    void finoAllAutorizzazioneDelCapoUnitaNonSiPuoPagare() {
        setSettings("segreteria", "PIE", "AGESCI Piemonte");
        JsonNode enrollment = enrolled("rover", publishOpen());

        assertThat(enrollment.get("payment").get("due").asDouble()).isEqualTo(150.0);
        assertThat(enrollment.get("paymentInfo").isNull()).isTrue();
        Result result = post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/payments", tokenOf("segreteria"),
                Map.of("amount", 50, "receivedOn", today().toString()));
        EventManagementTest.assertError(result, 409, 40910);
    }

    @Test
    void nei_posti_con_quota_si_vedono_importi_e_coordinate_della_regione() {
        setSettings("segreteria", "PIE", "AGESCI Piemonte");
        JsonNode enrollment = authorized("rover", publishOpen());

        JsonNode payment = enrollment.get("payment");
        assertThat(payment.get("fee").asDouble()).isEqualTo(150.0);
        assertThat(payment.get("paid").asDouble()).isZero();
        assertThat(payment.get("due").asDouble()).isEqualTo(150.0);
        JsonNode info = enrollment.get("paymentInfo");
        assertThat(info.get("holder").asString()).isEqualTo("AGESCI Piemonte");
        assertThat(info.get("iban").asString()).isEqualTo(IBAN);
        assertThat(info.get("hours").asString()).isEqualTo("Lun-Ven 9-13");
    }

    @Test
    void in_attesa_si_vede_la_quota_ma_non_dove_pagare() {
        setSettings("segreteria", "PIE", "AGESCI Piemonte");
        String eventId = publishOpen("seats", 1);
        enrolled("rover", eventId);

        JsonNode waiting = enrolled("rover-lig", eventId);

        assertThat(waiting.get("position").asString()).isEqualTo("WAITING");
        assertThat(waiting.get("payment").get("fee").asDouble()).isEqualTo(150.0);
        assertThat(waiting.get("paymentInfo").isNull()).isTrue();
    }

    @Test
    void un_evento_gratuito_non_ha_riepilogo_pagamento() {
        JsonNode enrollment = enrolled("rover", publishOpen("fee", 0));

        assertThat(enrollment.get("payment").isNull()).isTrue();
        assertThat(enrollment.get("paymentInfo").isNull()).isTrue();
    }

    @Test
    void una_regione_senza_impostazioni_da_coordinate_vuote() {
        JsonNode enrollment = authorized("rover", publishOpen());

        assertThat(enrollment.get("paymentInfo")).isNotNull();
        assertThat(enrollment.get("paymentInfo").get("iban").isNull()).isTrue();
        assertThat(enrollment.get("paymentInfo").get("holder").isNull()).isTrue();
    }

    @Test
    void un_evento_nazionale_usa_le_impostazioni_nazionali() {
        setSettings("segreteria", "PIE", "AGESCI Piemonte");
        setSettings("amministratore", "NAT", "AGESCI Nazionale");
        String eventId = publish(openEvent("level", "NATIONAL", "region", null), "amministratore").get("id").asString();

        JsonNode enrollment = authorized("rover", eventId);

        assertThat(enrollment.get("paymentInfo").get("holder").asString()).isEqualTo("AGESCI Nazionale");
    }
}
