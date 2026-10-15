package it.buonacaccia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.Census;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.RecipientRole;
import it.buonacaccia.model.User;
import it.buonacaccia.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Destinatari degli avvisi (research R6 della 004), con i repository simulati. */
class RecipientResolverTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RecipientResolver resolver = new RecipientResolver(users, mock(MongoTemplate.class));

    private final User participant = user("p");
    private final User presenter = user("leader");
    private final User otherLeader = user("leader2");

    @Test
    void registrazioneConfermaAlPartecipanteEAvvisoAlPresentatore() {
        when(users.findById("p")).thenReturn(Optional.of(participant));
        when(users.findById("leader")).thenReturn(Optional.of(presenter));

        List<Recipient> recipients = resolver.resolve(event(DomainEventType.ENROLLMENT_REGISTERED, "leader", null));

        assertThat(recipients).extracting(Recipient::role, Recipient::template).containsExactly(
                tuple(RecipientRole.PARTICIPANT, NotificationTemplate.ENROLLMENT_CONFIRMATION),
                tuple(RecipientRole.PRESENTER, NotificationTemplate.PRESENTER_NOTICE));
    }

    @Test
    void iCapiUnitaEsclusoIlPartecipanteStesso() {
        when(users.findByCensusUnitIdAndCensusMemberType("PIE-TO-01-RS", MemberType.LEADER))
                .thenReturn(List.of(participant, presenter, otherLeader));

        List<Recipient> recipients = resolver.resolve(event(DomainEventType.AUTHORIZATION_REQUESTED, null,
                ObligationType.UNIT_LEADER_AUTHORIZATION));

        assertThat(recipients).extracting(Recipient::userId).containsExactly("leader", "leader2");
        assertThat(recipients).allSatisfy(r -> assertThat(r.role()).isEqualTo(RecipientRole.UNIT_LEADER));
    }

    @Test
    void iCapiGruppoDelGruppoDelPartecipante() {
        when(users.findByCensusGroupIdAndCensusGroupLeaderTrue("PIE-TO-01")).thenReturn(List.of(otherLeader));

        List<Recipient> recipients = resolver.resolve(event(DomainEventType.AUTHORIZATION_REQUESTED, null,
                ObligationType.GROUP_LEADER_AUTHORIZATION));

        assertThat(recipients).extracting(Recipient::userId, Recipient::role)
                .containsExactly(tuple("leader2", RecipientRole.GROUP_LEADER));
    }

    @Test
    void ilPromemoriaVaSoloAlPartecipante() {
        when(users.findById("p")).thenReturn(Optional.of(participant));

        assertThat(resolver.resolve(event(DomainEventType.DEADLINE_REMINDER, "leader", null)))
                .extracting(Recipient::userId).containsExactly("p");
    }

    @Test
    void ipagamentiEIRimborsiVannoAPartecipanteEPresentatore() {
        when(users.findById("p")).thenReturn(Optional.of(participant));
        when(users.findById("leader")).thenReturn(Optional.of(presenter));

        assertThat(resolver.resolve(event(DomainEventType.PAYMENT_REGISTERED, "leader", null)))
                .extracting(Recipient::role, Recipient::template).containsExactly(
                        tuple(RecipientRole.PARTICIPANT, NotificationTemplate.PAYMENT_RECEIVED),
                        tuple(RecipientRole.PRESENTER, NotificationTemplate.PAYMENT_RECEIVED));
        assertThat(resolver.resolve(event(DomainEventType.REFUND_ISSUED, "leader", null)))
                .extracting(Recipient::template).containsOnly(NotificationTemplate.REFUND_ISSUED);
        assertThat(resolver.resolve(event(DomainEventType.REFUND_REQUESTED, null, null)))
                .extracting(Recipient::template).containsExactly(NotificationTemplate.REFUND_REQUESTED);
        assertThat(resolver.resolve(event(DomainEventType.REFUND_REJECTED, null, null)))
                .extracting(Recipient::template).containsExactly(NotificationTemplate.REFUND_REJECTED);
    }

    private static DomainEvent event(DomainEventType type, String presenterId, ObligationType obligationType) {
        DomainEvent.EnrollmentSnapshot enrollment = new DomainEvent.EnrollmentSnapshot("e1", "N-0001", "p", "Pia",
                "PIE-TO-01-RS", "PIE-TO-01", presenterId, null, null, null, null, List.of(), null, null, null, null, null);
        DomainEvent.EventSnapshot event = new DomainEvent.EventSnapshot("ev1", "C", "T", null, null, null, null);
        return new DomainEvent("d1", type, Instant.EPOCH, enrollment, event, obligationType, null, null);
    }

    private static User user(String id) {
        return User.builder().id(id).census(Census.builder().memberType(MemberType.LEADER).build()).build();
    }
}
