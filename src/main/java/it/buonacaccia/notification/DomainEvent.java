package it.buonacaccia.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.SeatPosition;

/**
 * Evento di dominio: il messaggio JSON del topic {@code buonacaccia.domain-events} (research R3).
 * Contiene una fotografia dei dati al momento del fatto, così le e-mail non dipendono dallo stato
 * attuale (FR-003). È un record perché non cambia mai dopo essere stato scritto.
 *
 * @param obligationType solo per {@code AUTHORIZATION_REQUESTED}
 * @param reminderDays   solo per {@code DEADLINE_REMINDER} (3 o 1)
 * @param money          solo per pagamenti e rimborsi (feature 005); null per gli altri eventi
 */
public record DomainEvent(String id, DomainEventType type, Instant occurredAt, EnrollmentSnapshot enrollment,
                          EventSnapshot event, ObligationType obligationType, Integer reminderDays,
                          MoneySnapshot money) {

    /** Iscrizione al momento del fatto. {@code waitingRank} solo per chi è in attesa. */
    public record EnrollmentSnapshot(String id, String number, String participantId, String participantName,
                                     String participantUnitId, String participantGroupId, String presenterId,
                                     String presenterName, EnrollmentStatus status, SeatPosition position,
                                     Integer waitingRank, List<ObligationType> pendingObligations, Instant deadline,
                                     BigDecimal fee, Boolean refundDue, ClosingReason closingReason,
                                     String closingNote) {
    }

    /**
     * Importi del pagamento o del rimborso, in centesimi (research R1 della 005). {@code paidCents} e
     * {@code dueCents} (residuo) solo per un pagamento; {@code date} è la data di incasso o di emissione;
     * {@code reason} il motivo del rifiuto.
     */
    public record MoneySnapshot(Long amountCents, Long paidCents, Long dueCents, LocalDate date, String reference,
                                String reason) {
    }

    /** Evento al momento del fatto. {@code changedFields} solo per {@code EVENT_CHANGED}. */
    public record EventSnapshot(String id, String code, String title, LocalDate startDate, LocalDate endDate,
                                String location, List<String> changedFields) {
    }
}
