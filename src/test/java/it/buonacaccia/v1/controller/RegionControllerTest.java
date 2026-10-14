package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Regioni AGESCI, lista fissa e pubblica (FR-022). */
class RegionControllerTest extends AbstractTest {

    @Test
    void regionsArePublic() {
        Result result = get("/api/v1/regions");

        assertThat(result.status()).isEqualTo(200);
        JsonNode regions = result.body().get("payload");
        assertThat(regions.size()).isEqualTo(20);
        assertThat(regions.valueStream().map(r -> r.get("code").asString())).contains("PIE", "LIG", "VEN");
        assertThat(regions.valueStream().filter(r -> r.get("code").asString().equals("PIE"))
                .findFirst().orElseThrow().get("name").asString()).isEqualTo("Piemonte");
    }
}
