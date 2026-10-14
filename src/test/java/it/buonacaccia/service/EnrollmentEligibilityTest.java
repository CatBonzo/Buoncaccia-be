package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import it.buonacaccia.model.Branch;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.CensusStatus;
import it.buonacaccia.model.EnrollmentMode;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.Requirements;
import it.buonacaccia.model.User;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import org.junit.jupiter.api.Test;

/** BR-01, BR-02 e BR-06: i requisiti personali, con tutti i motivi insieme (research R6). */
class EnrollmentEligibilityTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final LocalDate START = LocalDate.of(2027, 7, 20);

    @Test
    void roverMeetingEveryRequirementIsEligible() {
        assertThat(reasons(rover(), false, rossEvent())).isEmpty();
    }

    @Test
    void personWithoutCensusIsRejected() {
        User external = User.builder().id("x").birthDate(LocalDate.of(2009, 1, 1)).build();

        assertThat(fields(reasons(external, false, rossEvent()))).containsExactly("census");
    }

    @Test
    void censusNotInGoodStandingOrOfAnotherYearIsRejected() {
        User notInGoodStanding = rover();
        notInGoodStanding.getCensus().setStatus(CensusStatus.NOT_IN_GOOD_STANDING);
        User lastYear = rover();
        lastYear.getCensus().setYear(2026);

        assertThat(fields(reasons(notInGoodStanding, false, rossEvent()))).containsExactly("census");
        assertThat(fields(reasons(lastYear, false, rossEvent()))).containsExactly("census");
    }

    @Test
    void cubsAndScoutsCannotEnrollThemselves() {
        User scout = youth(Branch.EG, LocalDate.of(2013, 1, 1));
        Event event = event(EnrollmentMode.BOTH, Requirements.builder().branches(List.of(Branch.EG)).build());

        assertThat(fields(reasons(scout, false, event))).containsExactly("enrollmentMode");
        assertThat(reasons(scout, true, event)).isEmpty();
    }

    @Test
    void enrollmentModeOfTheEventIsRespected() {
        Event selfOnly = event(EnrollmentMode.SELF, null);
        Event leaderOnly = event(EnrollmentMode.BY_LEADER, null);

        assertThat(fields(reasons(rover(), false, leaderOnly))).containsExactly("enrollmentMode");
        assertThat(fields(reasons(rover(), true, selfOnly))).containsExactly("enrollmentMode");
        assertThat(reasons(rover(), true, leaderOnly)).isEmpty();
    }

    @Test
    void ageIsMeasuredOnTheStartDate() {
        Event event = event(EnrollmentMode.SELF, Requirements.builder().minAge(16).maxAge(21).build());
        // Compie 16 anni il giorno della partenza: ammesso.
        assertThat(reasons(withBirth(START.minusYears(16)), false, event)).isEmpty();
        // Li compie il giorno dopo: ha ancora 15 anni.
        assertThat(fields(reasons(withBirth(START.minusYears(16).plusDays(1)), false, event))).containsExactly("age");
        // 22 anni compiuti alla partenza: troppi.
        assertThat(fields(reasons(withBirth(START.minusYears(22)), false, event))).containsExactly("age");
    }

    @Test
    void missingBirthDateMattersOnlyWithAgeLimits() {
        Event withLimits = event(EnrollmentMode.SELF, Requirements.builder().minAge(16).build());
        Event withoutLimits = event(EnrollmentMode.SELF, Requirements.builder().build());

        assertThat(fields(reasons(withBirth(null), false, withLimits))).containsExactly("birthDate");
        assertThat(reasons(withBirth(null), false, withoutLimits)).isEmpty();
    }

    @Test
    void everyReasonIsReportedTogether() {
        User leader = User.builder().id("l").birthDate(LocalDate.of(1990, 1, 1))
                .census(census(Branch.CO, MemberType.LEADER)).build();

        assertThat(fields(reasons(leader, false, rossEvent()))).containsExactly("age", "branch", "memberType");
    }

    private static List<FieldErrorDTO> reasons(User participant, boolean byLeader, Event event) {
        return EnrollmentEligibility.personalReasons(participant, byLeader, event, TODAY);
    }

    private static List<String> fields(List<FieldErrorDTO> reasons) {
        return reasons.stream().map(FieldErrorDTO::getField).toList();
    }

    private static Event rossEvent() {
        return event(EnrollmentMode.SELF, Requirements.builder().minAge(16).maxAge(21)
                .branches(List.of(Branch.RS)).memberTypes(List.of(MemberType.YOUTH)).build());
    }

    private static Event event(EnrollmentMode mode, Requirements requirements) {
        return Event.builder().id("e").startDate(START).enrollmentMode(mode).requirements(requirements).build();
    }

    private static User rover() {
        return youth(Branch.RS, LocalDate.of(2009, 3, 1));
    }

    private static User withBirth(LocalDate birthDate) {
        User user = rover();
        user.setBirthDate(birthDate);
        return user;
    }

    private static User youth(Branch branch, LocalDate birthDate) {
        return User.builder().id("u").birthDate(birthDate).census(census(branch, MemberType.YOUTH)).build();
    }

    private static Census census(Branch branch, MemberType memberType) {
        return Census.builder().memberCode("1").groupId("G").unitId("U").branch(branch).memberType(memberType)
                .status(CensusStatus.IN_GOOD_STANDING).year(2027).build();
    }
}
