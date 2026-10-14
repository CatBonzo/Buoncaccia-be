package it.buonacaccia.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.Region;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Regola territoriale (FR-010, Q11): regione della segreteria da isMemberOf. */
class EventAccessPolicyTest {

    private final EventAccessPolicy policy = new EventAccessPolicy();

    @Test
    void adminManagesEverything() {
        CallerContext admin = caller("agesci:socio:false", "agesci:sistema:amministratore");

        for (EventLevel level : EventLevel.values()) {
            assertThat(policy.canManage(admin, level, Region.LIG)).isTrue();
        }
        assertThat(policy.canManage(admin, EventLevel.NATIONAL, null)).isTrue();
    }

    @Test
    void segreteriaManagesOnlyOwnRegion() {
        CallerContext pie = caller("agesci:PIE:segreteria", "agesci:regione:segreteria");

        assertThat(pie.region()).contains(Region.PIE);
        assertThat(policy.canManage(pie, EventLevel.REGIONAL, Region.PIE)).isTrue();
        assertThat(policy.canManage(pie, EventLevel.ZONE, Region.PIE)).isTrue();
        assertThat(policy.canManage(pie, EventLevel.REGIONAL, Region.LIG)).isFalse();
        assertThat(policy.canManage(pie, EventLevel.NATIONAL, null)).isFalse();
    }

    @Test
    void otherRegionIsForbidden() {
        CallerContext pie = caller("agesci:PIE:segreteria", "agesci:regione:segreteria");

        assertThatThrownBy(() -> policy.checkCanManage(pie, EventLevel.REGIONAL, Region.LIG))
                .isInstanceOf(BuonaCacciaException.class)
                .extracting(e -> ((BuonaCacciaException) e).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void missingOrUnknownRegionManagesNothing() {
        CallerContext unknown = caller("agesci:XYZ:segreteria", "agesci:regione:segreteria");
        CallerContext none = caller("agesci:socio:false", "agesci:regione:segreteria");

        assertThat(unknown.region()).isEmpty();
        assertThat(policy.canManage(unknown, EventLevel.REGIONAL, Region.PIE)).isFalse();
        assertThat(policy.canManage(none, EventLevel.REGIONAL, Region.PIE)).isFalse();
        assertThatThrownBy(() -> policy.checkManagesSomething(none)).isInstanceOf(BuonaCacciaException.class);
    }

    @Test
    void isMemberOfAsListIsRead() {
        Jwt jwt = jwt(List.of("agesci:socio:true", "agesci:LIG:segreteria"), "agesci:regione:segreteria");

        assertThat(CallerContext.from(jwt).region()).contains(Region.LIG);
    }

    @Test
    void anonymousManagesNothing() {
        assertThat(policy.canManage(CallerContext.anonymous(), EventLevel.REGIONAL, Region.PIE)).isFalse();
    }

    private static CallerContext caller(String isMemberOf, String role) {
        return CallerContext.from(jwt(isMemberOf, role));
    }

    private static Jwt jwt(Object isMemberOf, String role) {
        return Jwt.withTokenValue("t").header("alg", "none").subject("sub-test")
                .claim("isMemberOf", isMemberOf)
                .claim("role", List.of(role))
                .build();
    }
}
