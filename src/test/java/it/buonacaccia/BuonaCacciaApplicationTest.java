package it.buonacaccia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuonaCacciaApplicationTest extends ApiTestSupport {

    @Test
    void healthIsUpAndPublic() {
        Result result = get("/actuator/health");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("status").asString()).isEqualTo("UP");
    }
}
