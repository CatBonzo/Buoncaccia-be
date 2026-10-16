package it.buonacaccia.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.RecipientRole;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Testi delle e-mail (FR-014 della 004, research R7): numero o codice nell'oggetto, date italiane. */
class MailComposerTest {

    private static final MailComposer COMPOSER = new MailComposer();
    /** 22:30 UTC = 23:30 a Roma (ora solare dal 25/10). */
    private static final Instant DEADLINE = Instant.parse("2026-10-31T22:30:00Z");
    private static final User SARA = User.builder().id("u1").firstName("Sara").build();

    @ParameterizedTest
    @EnumSource(NotificationTemplate.class)
    void ogniModelloHaIlNumeroOIlCodiceNellOggettoEUnSaluto(NotificationTemplate template) {
        MailComposer.Mail mail = COMPOSER.compose(event(DomainEventType.ENROLLMENT_REGISTERED, SeatPosition.IN_SEATS, null),
                new Recipient(SARA, RecipientRole.PARTICIPANT, template, "e1"), "http://link/abc");

        assertThat(mail.subject()).containsAnyOf("PIE-2026-0001-0003", "PIE-2026-0001");
        assertThat(mail.text()).startsWith("Ciao Sara,").contains("Buona Caccia");
    }

    @Test
    void laScadenzaEScrittaAllOraItaliana() {
        MailComposer.Mail mail = COMPOSER.compose(event(DomainEventType.ENROLLMENT_REGISTERED, SeatPosition.IN_SEATS, null),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.ENROLLMENT_CONFIRMATION, "e1"), null);

        assertThat(mail.text()).contains("entro il 31/10/2026 alle 23:30").contains("dal 20/11/2026 al 25/11/2026");
    }

    @Test
    void laRichiestaDiAutorizzazioneContieneIlLink() {
        MailComposer.Mail mail = COMPOSER.compose(event(DomainEventType.AUTHORIZATION_REQUESTED, SeatPosition.IN_SEATS, null),
                new Recipient(SARA, RecipientRole.UNIT_LEADER, NotificationTemplate.AUTHORIZATION_REQUEST, "e1"),
                "http://localhost:4200/autorizza/abc");

        assertThat(mail.text()).contains("http://localhost:4200/autorizza/abc").contains("autorizzazione come capo unità");
    }

    @Test
    void ilRitiroDiceSeSpettaIlRimborso() {
        MailComposer.Mail refunded = COMPOSER.compose(event(DomainEventType.ENROLLMENT_WITHDRAWN, SeatPosition.IN_SEATS, true),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.ENROLLMENT_WITHDRAWN, "e1"), null);
        MailComposer.Mail notRefunded = COMPOSER.compose(event(DomainEventType.ENROLLMENT_WITHDRAWN, SeatPosition.IN_SEATS, false),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.ENROLLMENT_WITHDRAWN, "e1"), null);

        assertThat(refunded.text()).contains("La quota ti sarà rimborsata");
        assertThat(notRefunded.text()).contains("Non è previsto il rimborso");
    }

    @Test
    void ilPagamentoRicevutoDiceImportoVersatoEResiduo() {
        MailComposer.Mail mail = COMPOSER.compose(withMoney(DomainEventType.PAYMENT_REGISTERED,
                new DomainEvent.MoneySnapshot(5000L, 5000L, 10000L, LocalDate.of(2026, 10, 20), "CRO123", null)),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.PAYMENT_RECEIVED, "e1"), null);

        assertThat(mail.subject()).contains("PIE-2026-0001-0003");
        assertThat(mail.text()).contains("50,00 €").contains("Versato finora: 50,00 €").contains("Resta da pagare: 100,00 €");
    }

    @Test
    void ilPagamentoAnnullatoDiceImportoEMotivo() {
        MailComposer.Mail mail = COMPOSER.compose(withMoney(DomainEventType.PAYMENT_CANCELLED,
                new DomainEvent.MoneySnapshot(5000L, 0L, 15000L, LocalDate.of(2026, 10, 20), "CRO123",
                        "Registrato sull'iscrizione sbagliata")),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.PAYMENT_CANCELLED, "e1"), null);

        assertThat(mail.subject()).contains("Pagamento annullato").contains("PIE-2026-0001-0003");
        assertThat(mail.text()).contains("50,00 €").contains("20/10/2026")
                .contains("Motivo: Registrato sull'iscrizione sbagliata")
                .contains("Versato finora: 0,00 €").contains("Resta da pagare: 150,00 €");
    }

    @Test
    void ilRimborsoEmessoDiceImportoDataERiferimento() {
        MailComposer.Mail mail = COMPOSER.compose(withMoney(DomainEventType.REFUND_ISSUED,
                new DomainEvent.MoneySnapshot(15000L, null, null, LocalDate.of(2026, 11, 2), "BON-77", null)),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.REFUND_ISSUED, "e1"), null);

        assertThat(mail.text()).contains("150,00 €").contains("02/11/2026").contains("BON-77");
    }

    @Test
    void ilRimborsoRifiutatoDiceIlMotivoEQuelloRichiestoLImporto() {
        MailComposer.Mail rejected = COMPOSER.compose(withMoney(DomainEventType.REFUND_REJECTED,
                new DomainEvent.MoneySnapshot(15000L, null, null, null, null, "Ritiro tardivo")),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.REFUND_REJECTED, "e1"), null);
        MailComposer.Mail requested = COMPOSER.compose(withMoney(DomainEventType.REFUND_REQUESTED,
                new DomainEvent.MoneySnapshot(15000L, null, null, null, null, null)),
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.REFUND_REQUESTED, "e1"), null);

        assertThat(rejected.text()).contains("Motivo: Ritiro tardivo");
        assertThat(requested.text()).contains("rimborso di 150,00 €");
    }

    @Test
    void ilDocumentoRifiutatoDiceQualeERiceveIlMotivo() {
        DomainEvent base = event(DomainEventType.DOCUMENT_REJECTED, SeatPosition.IN_SEATS, null);
        DomainEvent rejected = new DomainEvent(base.id(), base.type(), base.occurredAt(), base.enrollment(), base.event(),
                null, null, null, new DomainEvent.DocumentSnapshot("FOTO", "Foto tessera", "Foto sfocata"));

        MailComposer.Mail mail = COMPOSER.compose(rejected,
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.DOCUMENT_REJECTED, "e1"), null);

        assertThat(mail.subject()).contains("PIE-2026-0001-0003");
        assertThat(mail.text()).contains("\"FOTO\"").contains("Motivo: Foto sfocata").contains("Carica un nuovo file");
    }

    @Test
    void laSchedaRiapertaDiceIlMotivoESenzaRisposte() {
        DomainEvent base = event(DomainEventType.FORM_REOPENED, SeatPosition.IN_SEATS, null);
        DomainEvent reopened = new DomainEvent(base.id(), base.type(), base.occurredAt(), base.enrollment(), base.event(),
                null, null, null, new DomainEvent.DocumentSnapshot(null, null, "Manca la parrocchia"));

        MailComposer.Mail mail = COMPOSER.compose(reopened,
                new Recipient(SARA, RecipientRole.PARTICIPANT, NotificationTemplate.FORM_REOPENED, "e1"), null);

        assertThat(mail.subject()).isEqualTo("Scheda da correggere per l'iscrizione PIE-2026-0001-0003");
        assertThat(mail.text()).contains("Motivo: Manca la parrocchia").contains("inviala di nuovo");
    }

    private static DomainEvent withMoney(DomainEventType type, DomainEvent.MoneySnapshot money) {
        DomainEvent base = event(type, SeatPosition.IN_SEATS, null);
        return new DomainEvent(base.id(), type, base.occurredAt(), base.enrollment(), base.event(), null, null, money, null);
    }

    private static DomainEvent event(DomainEventType type, SeatPosition position, Boolean refundDue) {
        DomainEvent.EnrollmentSnapshot enrollment = new DomainEvent.EnrollmentSnapshot("e1", "PIE-2026-0001-0003", "u2",
                "Rocco Rover", "PIE-TO-01-RS", "PIE-TO-01", null, null, EnrollmentStatus.REGISTERED, position, null,
                List.of(ObligationType.UNIT_LEADER_AUTHORIZATION, ObligationType.PAYMENT), DEADLINE,
                new BigDecimal("150"), refundDue, ClosingReason.WITHDRAWN, null);
        DomainEvent.EventSnapshot event = new DomainEvent.EventSnapshot("ev1", "PIE-2026-0001", "Route di prova",
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25), "Val Pellice", List.of("location"));
        return new DomainEvent("d1", type, DEADLINE, enrollment, event, ObligationType.UNIT_LEADER_AUTHORIZATION, 3, null, null);
    }
}
