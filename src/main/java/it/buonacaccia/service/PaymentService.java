package it.buonacaccia.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mongodb.MongoException;
import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.ClosingReason;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.Payment;
import it.buonacaccia.model.PaymentStatus;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.notification.DomainEventRecorder;
import it.buonacaccia.repository.EnrollmentRepository;
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.repository.PaymentRepository;
import it.buonacaccia.repository.RefundRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EnrollmentAccessPolicy;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.dto.PaymentRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pagamenti registrati dalla segreteria (feature 005, research R2, R4, R5). {@code Enrollment.paidCents}
 * lo cambia solo questo service, nella stessa transazione del pagamento; la versione dell'iscrizione
 * protegge due registrazioni contemporanee, e un conflitto si ritenta una volta rileggendo tutto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EventRepository eventRepository;
    private final EventAccessPolicy eventAccessPolicy;
    private final EnrollmentAccessPolicy enrollmentAccessPolicy;
    private final DomainEventRecorder domainEvents;
    private final CircuitBreakerService circuitBreakerService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @throws BuonaCacciaException {@code FORBIDDEN} fuori dalla propria regione, {@code ENROLLMENT_STATE_CONFLICT}
     *                              se l'iscrizione non è nei posti con un pagamento da fare, {@code INVALID_INPUT}
     *                              per importo o data non validi, {@code PAYMENT_DUPLICATE} per un doppione
     */
    public Payment register(CallerContext caller, String enrollmentId, PaymentRequestDTO request) {
        return retryOnConflict(() -> registerOnce(caller, enrollmentId, request));
    }

    /** Pagamenti di un'iscrizione, per chi la vede come partecipante o presentatore e per chi la gestisce. */
    public List<Payment> list(CallerContext caller, String enrollmentId) {
        Enrollment enrollment = find(enrollmentId);
        boolean allowed = enrollmentAccessPolicy.isParticipantOrPresenter(caller, enrollment)
                || eventAccessPolicy.canManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
        if (!allowed) {
            throw new BuonaCacciaException(ErrorCode.NOT_FOUND, "Iscrizione non trovata.");
        }
        return mongo(() -> paymentRepository.findByEnrollmentIdOrderByRegisteredAtAsc(enrollmentId));
    }

    /**
     * Annulla un pagamento registrato per errore: resta nel registro con il motivo (FR-006).
     *
     * @throws BuonaCacciaException {@code PAYMENT_CANCELLED} se è già annullato, {@code ENROLLMENT_STATE_CONFLICT}
     *                              se per l'iscrizione esiste già un rimborso
     */
    public Payment cancel(CallerContext caller, String paymentId, String reason) {
        return retryOnConflict(() -> cancelOnce(caller, paymentId, reason));
    }

    // ---------- registrazione ----------

    private Payment registerOnce(CallerContext caller, String enrollmentId, PaymentRequestDTO request) {
        Enrollment enrollment = find(enrollmentId);
        eventAccessPolicy.checkCanManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
        Instant now = clock.instant();
        EnrollmentObligation obligation = paymentToDo(enrollment);
        long amount = amountOf(request);
        checkDate(request.getReceivedOn(), now);
        long residual = feeOf(enrollment) - enrollment.getPaidCents();
        if (amount > residual) {
            throw invalid("amount", "L'importo supera il residuo da pagare (" + Money.toEuro(residual) + " €).");
        }
        String reference = request.getReference() == null || request.getReference().isBlank() ? null : request.getReference().trim();
        if (reference != null && isDuplicate(enrollmentId, amount, reference)) {
            throw new BuonaCacciaException(ErrorCode.PAYMENT_DUPLICATE);
        }
        Event event = mongo(() -> eventRepository.findById(enrollment.getEventId())).orElse(null);
        Payment payment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .enrollmentId(enrollment.getId())
                .enrollmentNumber(enrollment.getNumber())
                .eventId(enrollment.getEventId())
                .eventCode(enrollment.getEventCode())
                .eventLevel(enrollment.getEventLevel())
                .eventRegion(enrollment.getEventRegion())
                .participantId(enrollment.getParticipantId())
                .amountCents(amount)
                .method(request.getMethod())
                .receivedOn(request.getReceivedOn())
                .reference(reference)
                .status(PaymentStatus.VALID)
                .registeredBy(caller.getSubject())
                .registeredAt(now)
                .build();
        enrollment.setPaidCents(enrollment.getPaidCents() + amount);
        if (enrollment.getPaidCents() >= feeOf(enrollment)) {
            obligation.setStatus(ObligationStatus.SATISFIED);
            obligation.setSatisfiedBy(caller.getSubject());
            obligation.setSatisfiedAt(now);
            obligation.setNote("Pagamento registrato dalla segreteria");
        }
        EnrollmentService.completeIfDone(enrollment, now);
        enrollment.setUpdatedAt(now);
        inTransaction(() -> {
            paymentRepository.insert(payment);
            Enrollment saved = enrollmentRepository.save(enrollment);
            if (event != null) {
                domainEvents.payment(saved, event, amount, request.getReceivedOn(), reference);
            }
            return saved;
        });
        log.info("Iscrizione {}: pagamento di {} centesimi registrato da {}", enrollment.getNumber(), amount, caller.getSubject());
        return payment;
    }

    private Payment cancelOnce(CallerContext caller, String paymentId, String reason) {
        Payment payment = mongo(() -> paymentRepository.findById(paymentId))
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Pagamento non trovato."));
        eventAccessPolicy.checkCanManage(caller, payment.getEventLevel(), payment.getEventRegion());
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new BuonaCacciaException(ErrorCode.PAYMENT_CANCELLED);
        }
        Enrollment enrollment = find(payment.getEnrollmentId());
        if (mongo(() -> refundRepository.findByEnrollmentId(enrollment.getId())).isPresent()) {
            throw new BuonaCacciaException(ErrorCode.ENROLLMENT_STATE_CONFLICT,
                    "Per questa iscrizione esiste già un rimborso: il pagamento non si annulla più.");
        }
        Instant now = clock.instant();
        boolean wasCovered = enrollment.getPaidCents() >= feeOf(enrollment);
        enrollment.setPaidCents(enrollment.getPaidCents() - payment.getAmountCents());
        if (enrollment.isActive() && wasCovered && enrollment.getPaidCents() < feeOf(enrollment)) {
            reopenPayment(enrollment, now);
        }
        enrollment.setUpdatedAt(now);
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledBy(caller.getSubject());
        payment.setCancelledAt(now);
        payment.setCancelReason(reason);
        inTransaction(() -> {
            paymentRepository.save(payment);
            return enrollmentRepository.save(enrollment);
        });
        log.info("Iscrizione {}: pagamento {} annullato da {}", enrollment.getNumber(), paymentId, caller.getSubject());
        return payment;
    }

    /** L'adempimento tornato da fare: se l'iscrizione era completa torna registrata con una scadenza nuova (R5). */
    private static void reopenPayment(Enrollment enrollment, Instant now) {
        enrollment.getObligations().stream()
                .filter(o -> o.getType() == ObligationType.PAYMENT && o.getStatus() == ObligationStatus.SATISFIED)
                .forEach(o -> {
                    o.setStatus(ObligationStatus.TODO);
                    o.setSatisfiedBy(null);
                    o.setSatisfiedAt(null);
                    o.setNote(null);
                });
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.REGISTERED);
            enrollment.setCompletedAt(null);
            enrollment.setDeadline(ObligationPlanner.deadlineFrom(now));
        }
    }

    // ---------- controlli ----------

    private EnrollmentObligation paymentToDo(Enrollment enrollment) {
        boolean inSeats = enrollment.isActive() && enrollment.getPosition() == SeatPosition.IN_SEATS;
        Optional<EnrollmentObligation> obligation = enrollment.getObligations().stream()
                .filter(o -> o.getType() == ObligationType.PAYMENT && o.getStatus() == ObligationStatus.TODO)
                .findFirst();
        if (!inSeats || obligation.isEmpty()) {
            throw new BuonaCacciaException(ErrorCode.ENROLLMENT_STATE_CONFLICT,
                    "L'iscrizione non è nei posti con un pagamento ancora da fare.");
        }
        return obligation.get();
    }

    private static long amountOf(PaymentRequestDTO request) {
        try {
            return Money.toCents(request.getAmount());
        } catch (IllegalArgumentException e) {
            throw invalid("amount", e.getMessage());
        }
    }

    private static void checkDate(LocalDate receivedOn, Instant now) {
        if (receivedOn.isAfter(now.atZone(ClockConfig.ROME).toLocalDate())) {
            throw invalid("receivedOn", "La data di incasso non può essere nel futuro.");
        }
    }

    private boolean isDuplicate(String enrollmentId, long amount, String reference) {
        return mongo(() -> paymentRepository.findByEnrollmentIdAndStatus(enrollmentId, PaymentStatus.VALID)).stream()
                .anyMatch(p -> p.getAmountCents() == amount && reference.equalsIgnoreCase(p.getReference()));
    }

    static long feeOf(Enrollment enrollment) {
        return enrollment.getFeeCents() == null ? 0 : enrollment.getFeeCents();
    }

    private static BuonaCacciaException invalid(String field, String message) {
        return new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                List.of(new FieldErrorDTO(field, message)));
    }

    // ---------- accesso ai dati ----------

    private Enrollment find(String id) {
        return mongo(() -> enrollmentRepository.findById(id))
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Iscrizione non trovata."));
    }

    /**
     * Scrittura in transazione. Un conflitto con un'altra transazione sulla stessa iscrizione diventa
     * una versione superata (ignorata dal circuit breaker): {@link #retryOnConflict} ritenta una volta.
     */
    private <T> T inTransaction(Supplier<T> writes) {
        return mongo(() -> {
            try {
                return transactionTemplate.execute(tx -> writes.get());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (DataAccessException e) {
                if (isTransient(e)) {
                    throw new OptimisticLockingFailureException("Conflitto fra transazioni sulla stessa iscrizione", e);
                }
                throw e;
            }
        });
    }

    private <T> T retryOnConflict(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BuonaCacciaException e) {
            if (e.errorCode() != ErrorCode.STALE_VERSION) {
                throw e;
            }
            log.debug("Conflitto sull'iscrizione, riprovo una volta");
            return operation.get();
        }
    }

    private static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof MongoException mongo && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }
}
