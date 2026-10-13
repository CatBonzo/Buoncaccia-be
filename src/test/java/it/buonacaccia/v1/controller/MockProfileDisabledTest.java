package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/** US1 scenario 4: senza il profilo mocked le risorse /mock non esistono (FR-004). */
@ActiveProfiles(value = "default", inheritProfiles = false)
class MockProfileDisabledTest extends AbstractTest {

    @Test
    void findAllDoesNotExist() {
        Result result = get("/api/v1/mock/auth/findAll");

        assertThat(result.status()).isEqualTo(404);
        assertThat(result.body().get("errorCode").asInt()).isEqualTo(40400);
    }

    @Test
    void jwksDoesNotExist() {
        assertThat(get("/api/v1/mock/auth/jwks").status()).isEqualTo(404);
    }
}
