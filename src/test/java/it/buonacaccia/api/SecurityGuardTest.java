package it.buonacaccia.api;

import static org.assertj.core.api.Assertions.assertThat;

import it.buonacaccia.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ActiveProfiles;

/** US3: la guardia {@code @SecurityAccess} su entitlement e ruoli (FR-008, AUTH-1). */
@ActiveProfiles({"mocked", "probe"})
class SecurityGuardTest extends ApiTestSupport {

    @ParameterizedTest
    @CsvSource({
            "amministratore, 200",
            "segreteria, 200",
            "socio, 403",
            "esterno, 403"
    })
    void entitlementGuard(String username, int expectedStatus) {
        Result result = get("/api/v1/probe/entitlement", tokenOf(username));

        assertThat(result.status()).isEqualTo(expectedStatus);
        if (expectedStatus == 200) {
            assertThat(result.body().get("errorCode").asInt()).isZero();
            assertThat(result.body().get("payload").asString()).isEqualTo("ok");
        } else {
            assertForbidden(result);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "amministratore, 200",
            "segreteria, 403",
            "socio, 403"
    })
    void roleGuard(String username, int expectedStatus) {
        Result result = get("/api/v1/probe/role", tokenOf(username));

        assertThat(result.status()).isEqualTo(expectedStatus);
        if (expectedStatus == 403) {
            assertForbidden(result);
        }
    }

    @Test
    void tokenOnlyGuardAcceptsAnyValidToken() {
        assertThat(get("/api/v1/probe/token", tokenOf("esterno")).status()).isEqualTo(200);
        assertThat(get("/api/v1/probe/token").status()).isEqualTo(401);
    }

    private static void assertForbidden(Result result) {
        assertThat(result.body().get("errorCode").asInt()).isEqualTo(40300);
        assertThat(result.body().get("errorDomain").asString()).isEqualTo("AUTH");
        assertThat(result.body().get("errorMessage").asString()).isNotBlank();
        assertThat(result.body().get("payload").isNull()).isTrue();
    }
}
