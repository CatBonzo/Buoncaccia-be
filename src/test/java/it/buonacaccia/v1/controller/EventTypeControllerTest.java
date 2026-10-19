package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventFixtures.fields;
import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US4: tipi evento gestiti dall'amministrazione (FR-001…FR-003). */
class EventTypeControllerTest extends AbstractTest {

    private static final String TYPES = "/api/v1/event-types";

    @Test
    void mockTypesArePublic() {
        Result result = get(TYPES);

        assertThat(result.status()).isEqualTo(200);
        assertThat(codes(result.body().get("payload"))).contains(
                "PO", "CAPISQ", "ROSS", "PART", "EPPPI", "CFT", "CFM_LC", "CFM_EG", "CFM_RS", "CFA", "CCG", "MF_PT", "MF_AM_LC",
                "MF_AM_RS", "MF_AM_EG", "MF_INTER", "MF_CT", "ASSREG", "ASSZONA", "ALTROREG", "MF_CG", "MF_FS");
        assertThat(codes(result.body().get("payload"))).doesNotContain("SPEC_EG");
        assertThat(get(TYPES + "/PART").body().get("payload").has("name")).isFalse();
        assertThat(get(TYPES + "/ROSS").body().get("payload").get("category").asString()).isEqualTo("RS");
    }

    @Test
    void adminCreatesUpdatesAndDeactivates() {
        String admin = tokenOf("amministratore");
        String code = uniqueCode();

        Result created = post(TYPES, admin, Map.of("code", code, "category", "EG",
                "application", true, "defaultRequirements", Map.of("minAge", 12, "maxAge", 16)));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("payload").get("active").asBoolean()).isTrue();

        Result updated = put(TYPES + "/" + code, admin, Map.of("code", code, "category", "EG"));
        assertThat(updated.body().get("payload").get("category").asString()).isEqualTo("EG");

        Result deactivated = post(TYPES + "/" + code + "/deactivate", admin, null);
        assertThat(deactivated.body().get("payload").get("active").asBoolean()).isFalse();
        assertThat(codes(get(TYPES).body().get("payload"))).doesNotContain(code);
        assertThat(codes(get(TYPES + "?includeInactive=true", admin).body().get("payload"))).contains(code);
    }

    @Test
    void duplicateCodeIsAConflict() {
        String admin = tokenOf("amministratore");
        String code = uniqueCode();
        post(TYPES, admin, Map.of("code", code, "category", "LC"));

        assertError(post(TYPES, admin, Map.of("code", code, "category", "LC")), 409, 40902);
    }

    @Test
    void invalidTypeListsFields() {
        Result result = post(TYPES, tokenOf("amministratore"), Map.of("code", "minuscolo"));

        assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).contains("code", "category");
    }

    @Test
    void managementNeedsEntitlement() {
        Map<String, Object> body = Map.of("code", uniqueCode(), "category", "EG");

        assertError(post(TYPES, null, body), 401, 40100);
        assertError(post(TYPES, tokenOf("segreteria"), body), 403, 40300);
        assertError(post(TYPES + "/ROSS/deactivate", tokenOf("segreteria"), null), 403, 40300);
        assertError(get(TYPES + "?includeInactive=true", tokenOf("segreteria")), 403, 40300);
    }

    @Test
    void unknownTypeIsNotFound() {
        assertError(get(TYPES + "/NON_ESISTE"), 404, 40400);
        assertError(put(TYPES + "/NON_ESISTE", tokenOf("amministratore"),
                Map.of("code", "NON_ESISTE", "category", "EG")), 404, 40400);
    }

    private static String uniqueCode() {
        return "T_" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    private static java.util.List<String> codes(JsonNode types) {
        return types.valueStream().map(t -> t.get("code").asString()).toList();
    }
}
