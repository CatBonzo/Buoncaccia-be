package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US1: ottenere un token di prova (FR-001…FR-006). */
class MockAuthControllerTest extends AbstractTest {

    @Test
    void findAllReturnsEveryMockUserWithIssuerAndToken() {
        Result result = get("/api/v1/mock/auth/findAll");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("errorCode").asInt()).isZero();
        JsonNode users = result.body().get("payload");
        List<String> usernames = new ArrayList<>();
        for (JsonNode user : users) {
            usernames.add(user.get("username").asString());
            assertThat(user.get("issuer").asString()).isEqualTo("mock-auth");
            assertThat(user.get("accessToken").asString()).isNotBlank();
            assertThat(user.get("agesciUUID").asString()).isNotBlank();
            assertThat(user.get("authentication").get("role").size()).isPositive();
        }
        assertThat(usernames).containsExactlyInAnyOrder("amministratore", "segreteria", "socio", "esterno",
                "segreteria-lig", "esploratore", "lupetto", "rover", "rover-lig", "capogruppo",
                "socio-non-in-regola", "scolta");
    }

    @Test
    void fixedBuonaCacciaIdIsKept() {
        JsonNode users = get("/api/v1/mock/auth/findAll").body().get("payload");
        for (JsonNode user : users) {
            if ("amministratore".equals(user.get("username").asString())) {
                assertThat(user.get("buonaCacciaUUID").asString()).isEqualTo("user-001");
            }
        }
    }

    @Test
    void everyTokenIsAcceptedAsItsOwnUser() {
        for (String username : List.of("amministratore", "segreteria", "socio", "esterno")) {
            Result me = get("/api/v1/users/me", tokenOf(username));

            assertThat(me.status()).isEqualTo(200);
            assertThat(me.body().get("payload").get("username").asString()).isEqualTo(username);
        }
    }

    @Test
    void jwksExposesOneRsaSigningKey() {
        Result result = get("/api/v1/mock/auth/jwks");

        assertThat(result.status()).isEqualTo(200);
        JsonNode key = result.body().get("keys").get(0);
        assertThat(key.get("kty").asString()).isEqualTo("RSA");
        assertThat(key.get("kid").asString()).isNotBlank();
        assertThat(key.has("d")).as("la chiave privata non deve uscire").isFalse();
    }
}
