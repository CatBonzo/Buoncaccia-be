package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import it.buonacaccia.config.MockedIdentityProperties.Authentication;
import it.buonacaccia.config.MockedIdentityProperties.MockedUser;
import org.junit.jupiter.api.Test;

/** Caso limite: due voci del file con lo stesso agesciUUID fermano l'avvio. */
class MockIdentityServiceTest {

    @Test
    void duplicateAgesciUuidIsRejected() {
        Authentication auth = new Authentication("agesci:socio:false", List.of("agesci:socio:user"), List.of());
        MockedUser first = new MockedUser("dup", null, "Uno", "uno", "Uno", "Uno", null, auth, null);
        MockedUser second = new MockedUser("dup", null, "Due", "due", "Due", "Due", null, auth, null);
        MockIdentityService service = new MockIdentityService(null, null, null);

        assertThatThrownBy(() -> service.load(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup");
    }
}
