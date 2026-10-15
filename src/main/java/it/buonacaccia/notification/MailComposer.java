package it.buonacaccia.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.model.User;
import it.buonacaccia.service.Money;
import org.springframework.stereotype.Component;

/**
 * Oggetto e testo delle e-mail (FR-008…FR-014 della 004, research R7): testo semplice in italiano,
 * date nell'ora italiana, sempre con il numero dell'iscrizione o il codice dell'evento.
 */
@Component
public class MailComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'alle' HH:mm");
    private static final String SIGNATURE = "\n\nBuona Caccia\n(e-mail automatica, non rispondere)\n";

    /** E-mail pronta da inviare. */
    public record Mail(String subject, String text) {
    }

    /** @param link solo per {@code AUTHORIZATION_REQUEST} */
    public Mail compose(DomainEvent event, Recipient recipient, String link) {
        DomainEvent.EnrollmentSnapshot e = event.enrollment();
        DomainEvent.EventSnapshot ev = event.event();
        String hello = "Ciao " + firstName(recipient.user()) + ",\n\n";
        return switch (recipient.template()) {
            case ENROLLMENT_CONFIRMATION -> new Mail(
                    "Iscrizione " + e.number() + " registrata - " + ev.title(),
                    hello + "la tua iscrizione all'evento " + eventLine(ev) + " è registrata con il numero "
                            + e.number() + ".\n\n" + position(e) + todo(e) + SIGNATURE);
            case PRESENTER_NOTICE -> new Mail(
                    "Hai iscritto " + e.participantName() + " - " + e.number(),
                    hello + "hai iscritto " + e.participantName() + " all'evento " + eventLine(ev)
                            + ". Numero dell'iscrizione: " + e.number() + ".\n\n" + position(e) + todo(e) + SIGNATURE);
            case AUTHORIZATION_REQUEST -> new Mail(
                    "Autorizzazione richiesta per " + e.participantName() + " - " + e.number(),
                    hello + e.participantName() + " si è iscritto all'evento " + eventLine(ev)
                            + " (iscrizione " + e.number() + ") e serve la tua " + label(event.obligationType()) + ".\n\n"
                            + "Puoi autorizzare o negare da qui, senza entrare nel sito:\n" + link + "\n\n"
                            + "Il link vale una sola volta e scade con l'iscrizione"
                            + (e.deadline() == null ? "." : ", il " + dateTime(e.deadline()) + ".") + SIGNATURE);
            case DEADLINE_REMINDER -> new Mail(
                    "Promemoria: l'iscrizione " + e.number() + " scade il " + date(e.deadline()),
                    hello + "mancano " + (event.reminderDays() == 1 ? "1 giorno" : event.reminderDays() + " giorni")
                            + " alla scadenza dell'iscrizione " + e.number() + " all'evento " + eventLine(ev) + ".\n\n"
                            + todo(e) + "Se non completi entro la scadenza l'iscrizione scade e il posto passa ad altri."
                            + SIGNATURE);
            case ENROLLMENT_COMPLETED -> new Mail(
                    "Iscrizione " + e.number() + " completata",
                    hello + "l'iscrizione " + e.number() + " di " + e.participantName() + " all'evento " + eventLine(ev)
                            + " è completa: non resta nulla da fare. Buona strada!" + SIGNATURE);
            case ENROLLMENT_EXPIRED -> new Mail(
                    "Iscrizione " + e.number() + " scaduta",
                    hello + "l'iscrizione " + e.number() + " di " + e.participantName() + " all'evento " + eventLine(ev)
                            + " è scaduta perché non è stata completata entro la scadenza. Il posto è passato ad altri."
                            + SIGNATURE);
            case ENROLLMENT_WITHDRAWN -> new Mail(
                    "Iscrizione " + e.number() + " ritirata",
                    hello + "l'iscrizione " + e.number() + " di " + e.participantName() + " all'evento " + eventLine(ev)
                            + " è stata ritirata.\n\n" + refund(e) + SIGNATURE);
            case ENROLLMENT_PROMOTED -> new Mail(
                    "Iscrizione " + e.number() + ": un posto per te",
                    hello + "si è liberato un posto: l'iscrizione " + e.number() + " di " + e.participantName()
                            + " all'evento " + eventLine(ev) + " ora è nei posti.\n\n" + payment(e) + todo(e) + SIGNATURE);
            case ENROLLMENT_REJECTED -> new Mail(
                    "Iscrizione " + e.number() + " non accettata",
                    hello + "l'iscrizione " + e.number() + " di " + e.participantName() + " all'evento " + eventLine(ev)
                            + " non è stata accettata.\nMotivo: " + nullToDash(e.closingNote()) + SIGNATURE);
            case ENROLLMENT_CANCELLED -> new Mail(
                    "Iscrizione " + e.number() + " annullata",
                    hello + "l'iscrizione " + e.number() + " di " + e.participantName() + " all'evento " + eventLine(ev)
                            + " è annullata: " + cancellation(e) + "\n\n" + refund(e) + SIGNATURE);
            case EVENT_CHANGED -> new Mail(
                    "Evento " + ev.code() + " modificato - " + ev.title(),
                    hello + "l'evento " + ev.title() + " (" + ev.code() + "), a cui sei iscritto, è cambiato.\n\n"
                            + changes(ev) + "\nLa tua iscrizione resta valida; se non puoi più partecipare puoi ritirarla dal sito."
                            + SIGNATURE);
            case PAYMENT_RECEIVED -> new Mail(
                    "Pagamento ricevuto per l'iscrizione " + e.number(),
                    hello + "la segreteria ha registrato un pagamento di " + cents(m(event).amountCents())
                            + " per l'iscrizione " + e.number() + " di " + e.participantName() + " all'evento "
                            + eventLine(ev) + ".\n\nVersato finora: " + cents(m(event).paidCents()) + ". Resta da pagare: "
                            + cents(m(event).dueCents()) + ".\n"
                            + (e.status() == EnrollmentStatus.COMPLETED ? "L'iscrizione è completa." : todo(e)) + SIGNATURE);
            case REFUND_REQUESTED -> new Mail(
                    "Rimborso richiesto per l'iscrizione " + e.number(),
                    hello + "per l'iscrizione " + e.number() + " di " + e.participantName() + " all'evento " + eventLine(ev)
                            + " è stato richiesto il rimborso di " + cents(m(event).amountCents())
                            + ".\nLa segreteria lo lavorerà e ti avviseremo appena sarà emesso." + SIGNATURE);
            case REFUND_ISSUED -> new Mail(
                    "Rimborso emesso per l'iscrizione " + e.number(),
                    hello + "il rimborso di " + cents(m(event).amountCents()) + " per l'iscrizione " + e.number()
                            + " all'evento " + eventLine(ev) + " è stato emesso il " + date(m(event).date())
                            + (m(event).reference() == null ? "" : ".\nRiferimento: " + m(event).reference()) + "."
                            + SIGNATURE);
            case REFUND_REJECTED -> new Mail(
                    "Rimborso non accordato per l'iscrizione " + e.number(),
                    hello + "il rimborso di " + cents(m(event).amountCents()) + " per l'iscrizione " + e.number()
                            + " all'evento " + eventLine(ev) + " non è stato accordato.\nMotivo: "
                            + nullToDash(m(event).reason()) + SIGNATURE);
        };
    }

    private static DomainEvent.MoneySnapshot m(DomainEvent event) {
        return event.money() == null ? new DomainEvent.MoneySnapshot(null, null, null, null, null, null) : event.money();
    }

    private static String cents(Long cents) {
        return cents == null ? "-" : euro(Money.toEuro(cents));
    }

    /** Nome leggibile di un adempimento. */
    public static String label(ObligationType type) {
        return switch (type) {
            case LEADER_VALIDATION -> "validazione del capo";
            case UNIT_LEADER_AUTHORIZATION -> "autorizzazione come capo unità";
            case GROUP_LEADER_AUTHORIZATION -> "autorizzazione come capo gruppo";
            case PAYMENT -> "pagamento della quota";
            case DOCUMENT -> "documento richiesto";
            case ELECTRONIC_FORM -> "modulo elettronico";
        };
    }

    private static String eventLine(DomainEvent.EventSnapshot ev) {
        return ev.title() + " (" + ev.code() + ", dal " + date(ev.startDate()) + " al " + date(ev.endDate())
                + (ev.location() == null ? "" : ", " + ev.location()) + ")";
    }

    private static String position(DomainEvent.EnrollmentSnapshot e) {
        if (e.position() == SeatPosition.WAITING) {
            return "Posizione: in attesa" + (e.waitingRank() == null ? "" : ", numero " + e.waitingRank() + " in lista")
                    + ". Se si libera un posto ti scriviamo; il pagamento si chiede solo allora.\n\n";
        }
        return "Posizione: nei posti.\n\n";
    }

    private static String todo(DomainEvent.EnrollmentSnapshot e) {
        if (e.pendingObligations() == null || e.pendingObligations().isEmpty()) {
            return "Non resta nulla da fare.\n";
        }
        String items = e.pendingObligations().stream().map(t -> "- " + label(t)).collect(Collectors.joining("\n"));
        return "Da completare" + (e.deadline() == null ? "" : " entro il " + dateTime(e.deadline())) + ":\n" + items + "\n";
    }

    private static String payment(DomainEvent.EnrollmentSnapshot e) {
        boolean pays = e.pendingObligations() != null && e.pendingObligations().contains(ObligationType.PAYMENT);
        return pays ? "Ora va pagata la quota di " + euro(e.fee()) + ".\n" : "";
    }

    private static String refund(DomainEvent.EnrollmentSnapshot e) {
        if (Boolean.TRUE.equals(e.refundDue())) {
            return "La quota ti sarà rimborsata.";
        }
        return e.position() == SeatPosition.IN_SEATS && e.fee() != null && e.fee().signum() > 0
                ? "Non è previsto il rimborso della quota." : "Non c'è nessuna quota da rimborsare.";
    }

    private static String cancellation(DomainEvent.EnrollmentSnapshot e) {
        if (e.closingReason() == ClosingReason.EVENT_CANCELLED) {
            return "l'evento è stato annullato. Motivo: " + nullToDash(e.closingNote()) + ".";
        }
        return "alla partenza non si è liberato nessun posto.";
    }

    private static String changes(DomainEvent.EventSnapshot ev) {
        List<String> fields = ev.changedFields() == null ? List.of() : ev.changedFields();
        StringBuilder text = new StringBuilder();
        if (fields.contains("startDate") || fields.contains("endDate")) {
            text.append("Nuove date: dal ").append(date(ev.startDate())).append(" al ").append(date(ev.endDate())).append("\n");
        }
        if (fields.contains("location")) {
            text.append("Nuovo luogo: ").append(nullToDash(ev.location())).append("\n");
        }
        return text.toString();
    }

    private static String firstName(User user) {
        return user == null || user.getFirstName() == null ? "" : user.getFirstName();
    }

    private static String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    private static String date(Instant instant) {
        return instant == null ? "-" : DATE.format(instant.atZone(ClockConfig.ROME));
    }

    private static String dateTime(Instant instant) {
        return DATE_TIME.format(instant.atZone(ClockConfig.ROME));
    }

    private static String euro(BigDecimal amount) {
        return amount == null ? "-" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
