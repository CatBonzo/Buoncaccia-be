package it.buonacaccia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.Payment;
import it.buonacaccia.model.PaymentStatus;
import it.buonacaccia.model.Refund;
import it.buonacaccia.model.RefundStatus;
import it.buonacaccia.model.SeatPosition;
import it.buonacaccia.repository.EnrollmentRepository;
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.repository.PaymentRepository;
import it.buonacaccia.repository.RefundRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.ReconciliationDTO;
import it.buonacaccia.v1.mapper.PaymentMapper;
import it.buonacaccia.v1.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Quadro dei pagamenti di un evento e CSV (feature 005, research R11). I totali si calcolano dai
 * pagamenti e dai rimborsi in centesimi interi: nessun arrotondamento.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final EventRepository eventRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final EventAccessPolicy eventAccessPolicy;
    private final CircuitBreakerService circuitBreakerService;

    /**
     * @throws BuonaCacciaException {@code NOT_FOUND} se l'evento non esiste, {@code FORBIDDEN} se il chiamante
     *                              non gestisce quell'evento
     */
    public ReconciliationDTO get(CallerContext caller, String eventId) {
        Snapshot data = load(caller, eventId);
        long collected = 0;
        long cancelled = 0;
        for (Payment payment : data.payments()) {
            if (payment.getStatus() == PaymentStatus.VALID) {
                collected += payment.getAmountCents();
            } else {
                cancelled += payment.getAmountCents();
            }
        }
        long toRefund = sumRefunds(data.refunds(), RefundStatus.REQUESTED) + sumRefunds(data.refunds(), RefundStatus.IN_PROGRESS);
        List<ReconciliationDTO.Outstanding> outstanding = data.enrollments().stream()
                .filter(e -> e.isActive() && e.getPosition() == SeatPosition.IN_SEATS
                        && PaymentService.feeOf(e) - e.getPaidCents() > 0)
                .map(e -> ReconciliationDTO.Outstanding.builder()
                        .enrollmentNumber(e.getNumber())
                        .participantName(e.getParticipantName())
                        .fee(Money.toEuro(PaymentService.feeOf(e)))
                        .paid(Money.toEuro(e.getPaidCents()))
                        .due(Money.toEuro(PaymentService.feeOf(e) - e.getPaidCents()))
                        .build())
                .toList();
        return ReconciliationDTO.builder()
                .eventId(data.event().getId())
                .eventCode(data.event().getCode())
                .collected(Money.toEuro(collected))
                .cancelled(Money.toEuro(cancelled))
                .toRefund(Money.toEuro(toRefund))
                .refunded(Money.toEuro(sumRefunds(data.refunds(), RefundStatus.ISSUED)))
                .outstanding(outstanding)
                .payments(PaymentMapper.INSTANCE.toDtoList(data.payments()))
                .refunds(RefundMapper.INSTANCE.toDtoList(data.refunds()))
                .build();
    }

    /** Lo stesso quadro in CSV: una riga per pagamento e per rimborso (FR-015). */
    public String csv(CallerContext caller, String eventId) {
        Snapshot data = load(caller, eventId);
        Map<String, Enrollment> byId = data.enrollments().stream().collect(Collectors.toMap(Enrollment::getId, Function.identity()));
        List<ReconciliationCsv.Row> rows = new ArrayList<>();
        for (Payment p : data.payments()) {
            Enrollment e = byId.get(p.getEnrollmentId());
            rows.add(new ReconciliationCsv.Row("PAGAMENTO", p.getEnrollmentNumber(), e == null ? null : e.getParticipantName(),
                    Money.toEuro(p.getAmountCents()), p.getMethod().name(), p.getStatus().name(), p.getReceivedOn(),
                    p.getReference(), p.getRegisteredBy()));
        }
        for (Refund r : data.refunds()) {
            rows.add(new ReconciliationCsv.Row("RIMBORSO", r.getEnrollmentNumber(), r.getParticipantName(),
                    Money.toEuro(r.getAmountCents()), null, r.getStatus().name(),
                    r.getIssuedOn() != null ? r.getIssuedOn()
                            : r.getCreatedAt().atZone(it.buonacaccia.config.ClockConfig.ROME).toLocalDate(),
                    r.getReference(), r.getProcessedBy() != null ? r.getProcessedBy() : r.getTakenBy()));
        }
        return ReconciliationCsv.render(rows);
    }

    private Snapshot load(CallerContext caller, String eventId) {
        Event event = mongo(() -> eventRepository.findById(eventId))
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.NOT_FOUND, "Evento non trovato."));
        eventAccessPolicy.checkCanManage(caller, event.getLevel(), event.getRegion());
        return mongo(() -> new Snapshot(event, enrollmentRepository.findByEventId(eventId),
                paymentRepository.findByEventIdOrderByRegisteredAtAsc(eventId),
                refundRepository.findByEventIdOrderByCreatedAtAsc(eventId)));
    }

    private static long sumRefunds(List<Refund> refunds, RefundStatus status) {
        return refunds.stream().filter(r -> r.getStatus() == status).mapToLong(Refund::getAmountCents).sum();
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }

    private record Snapshot(Event event, List<Enrollment> enrollments, List<Payment> payments, List<Refund> refunds) {
    }
}
