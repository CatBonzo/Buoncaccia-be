package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventFixtures.fields;
import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;

/** US5: coordinate di pagamento per regione (FR-016…FR-018 della 005). */
class RegionSettingsTest extends AbstractTest {

    private static final String SETTINGS = "/api/v1/region-settings/";
    private static final String IBAN = "IT60X0542811101000000123456";

    @Test
    void laSegreteriaSalvaELeggeLeProprieCoordinate() {
        String token = tokenOf("segreteria");

        Result saved = put(SETTINGS + "PIE", token, Map.of("holder", "AGESCI Piemonte", "iban", "it60 x054 2811 1010 0000 0123 456",
                "contactEmail", "segreteria@example.org", "contactPhone", "0110000000", "hours", "Lun-Ven 9-13"));

        assertThat(saved.status()).isEqualTo(200);
        assertThat(saved.body().get("payload").get("iban").asString()).isEqualTo(IBAN);
        Result read = get(SETTINGS + "PIE", token);
        assertThat(read.body().get("payload").get("holder").asString()).isEqualTo("AGESCI Piemonte");
        assertThat(read.body().get("payload").get("hours").asString()).isEqualTo("Lun-Ven 9-13");
    }

    @Test
    void unIbanNonValidoElencaIlCampo() {
        Result result = put(SETTINGS + "PIE", tokenOf("segreteria"), Map.of("iban", "IT00X0542811101000000123456"));

        assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).contains("iban");
    }

    @Test
    void laSegreteriaNonCambiaLeCoordinateDiUnAltraRegione() {
        assertError(put(SETTINGS + "PIE", tokenOf("segreteria-lig"), Map.of("holder", "Intruso")), 403, 40300);
    }

    @Test
    void leCoordinateNazionaliLeCuraSoloLAmministrazione() {
        assertError(put(SETTINGS + "NAT", tokenOf("segreteria"), Map.of("holder", "No")), 403, 40300);
        Result result = put(SETTINGS + "NAT", tokenOf("amministratore"), Map.of("holder", "AGESCI Nazionale", "iban", IBAN));
        assertThat(result.status()).isEqualTo(200);
        assertThat(get(SETTINGS + "NAT", tokenOf("segreteria")).body().get("payload").get("holder").asString())
                .isEqualTo("AGESCI Nazionale");
    }

    @Test
    void unaSiglaInesistenteNonSiTrova() {
        assertError(get(SETTINGS + "XXX", tokenOf("amministratore")), 404, 40400);
    }

    @Test
    void unaRegioneMaiImpostataHaICampiVuoti() {
        Result result = get(SETTINGS + "VDA", tokenOf("amministratore"));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("payload").get("iban").isNull()).isTrue();
        assertThat(result.body().get("payload").get("key").asString()).isEqualTo("VDA");
    }

    @Test
    void serveIlTokenConLEntitlement() {
        assertError(get(SETTINGS + "PIE", null), 401, 40100);
        assertError(get(SETTINGS + "PIE", tokenOf("socio")), 403, 40300);
    }
}
