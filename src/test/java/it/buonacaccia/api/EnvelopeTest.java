package it.buonacaccia.api;

import static org.assertj.core.api.Assertions.assertThat;

import it.buonacaccia.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

/** US4: ogni risposta ha l'envelope (FR-013…FR-015). */
@ActiveProfiles({"mocked", "probe"})
class EnvelopeTest extends ApiTestSupport {

    @Test
    void successHasTheFourFields() {
        assertEnvelope(get("/api/v1/users/me", tokenOf("socio")).body(), 0);
    }

    @Test
    void unknownResourceIsNotFound() {
        Result result = get("/api/v1/nonesiste", tokenOf("socio"));

        assertThat(result.status()).isEqualTo(404);
        assertEnvelope(result.body(), 40400);
    }

    @Test
    void unexpectedErrorHidesInternalDetails() {
        Result result = get("/api/v1/probe/boom", tokenOf("socio"));

        assertThat(result.status()).isEqualTo(500);
        assertEnvelope(result.body(), 50000);
        assertThat(result.body().toString()).doesNotContain("dettaglio interno", "IllegalStateException", "at it.");
    }

    private static void assertEnvelope(JsonNode body, int errorCode) {
        assertThat(body.has("errorCode")).isTrue();
        assertThat(body.has("errorMessage")).isTrue();
        assertThat(body.has("errorDomain")).isTrue();
        assertThat(body.has("payload")).isTrue();
        assertThat(body.get("errorCode").asInt()).isEqualTo(errorCode);
    }
}
