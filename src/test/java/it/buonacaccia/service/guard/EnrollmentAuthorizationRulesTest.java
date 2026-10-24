package it.buonacaccia.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import it.buonacaccia.model.Branch;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.User;
import it.buonacaccia.service.PaymentRules;
import org.junit.jupiter.api.Test;

/** 24/10: il capo unità autorizza la sua unità, il capo gruppo tutto il gruppo; basta uno dei due per pagare. */
class EnrollmentAuthorizationRulesTest {

    private final EnrollmentAccessPolicy policy = new EnrollmentAccessPolicy(null, null);

    private static Enrollment enrollment(ObligationStatus unit, ObligationStatus group) {
        return Enrollment.builder().participantId("p").participantGroupId("G1").participantUnitId("G1-LC")
                .participantBranch(Branch.LC)
                .obligations(List.of(obligation(ObligationType.UNIT_LEADER_AUTHORIZATION, unit),
                        obligation(ObligationType.GROUP_LEADER_AUTHORIZATION, group)))
                .build();
    }

    private static EnrollmentObligation obligation(ObligationType type, ObligationStatus status) {
        return EnrollmentObligation.builder().type(type).status(status).build();
    }

    private static User leader(String id, String groupId, String unitId, boolean groupLeader) {
        return User.builder().id(id).census(Census.builder().memberType(MemberType.LEADER).groupId(groupId).unitId(unitId)
                .groupLeader(groupLeader).build()).build();
    }

    @Test
    void ilCapoGruppoAutorizzaTuttoIlSuoGruppoIlCapoUnitaSoloLaSuaUnita() {
        Enrollment e = enrollment(ObligationStatus.TODO, ObligationStatus.TODO);

        assertThat(policy.canAuthorize(leader("u", "G1", "G1-LC", false), e, ObligationType.UNIT_LEADER_AUTHORIZATION)).isTrue();
        assertThat(policy.canAuthorize(leader("u", "G1", "G1-EG", false), e, ObligationType.UNIT_LEADER_AUTHORIZATION)).isFalse();
        assertThat(policy.canAuthorize(leader("g", "G1", "G1-EG", true), e, ObligationType.UNIT_LEADER_AUTHORIZATION)).isTrue();
        assertThat(policy.canAuthorize(leader("g", "G1", "G1-EG", true), e, ObligationType.GROUP_LEADER_AUTHORIZATION)).isTrue();
        assertThat(policy.canAuthorize(leader("g", "G2", "G2-EG", true), e, ObligationType.UNIT_LEADER_AUTHORIZATION)).isFalse();
    }

    @Test
    void perPagareBastaLAutorizzazioneDelCapoUnitaODelCapoGruppo() {
        assertThat(PaymentRules.authorized(enrollment(ObligationStatus.TODO, ObligationStatus.TODO))).isFalse();
        assertThat(PaymentRules.authorized(enrollment(ObligationStatus.SATISFIED, ObligationStatus.TODO))).isTrue();
        assertThat(PaymentRules.authorized(enrollment(ObligationStatus.TODO, ObligationStatus.SATISFIED))).isTrue();
        assertThat(PaymentRules.authorized(enrollment(ObligationStatus.REJECTED, ObligationStatus.TODO))).isFalse();
    }
}
