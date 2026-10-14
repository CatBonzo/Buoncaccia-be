package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import it.buonacaccia.model.SeatPosition;
import org.junit.jupiter.api.Test;

/** BR-10 (Q12): rimborso con ritiro almeno 7 giorni prima della partenza, data di partenza esclusa. */
class RefundRuleTest {

    private static final LocalDate START = LocalDate.of(2027, 7, 20);
    private static final BigDecimal FEE = new BigDecimal("150.00");

    @Test
    void sevenDaysBeforeGetsARefund() {
        assertThat(RefundRule.refundDue(FEE, START, SeatPosition.IN_SEATS, at("2027-07-13T10:00:00Z"))).isTrue();
    }

    @Test
    void sixDaysBeforeDoesNot() {
        assertThat(RefundRule.refundDue(FEE, START, SeatPosition.IN_SEATS, at("2027-07-14T10:00:00Z"))).isFalse();
    }

    @Test
    void italianDateDecidesAroundMidnight() {
        // 13/07 alle 23:30 UTC è già il 14/07 in Italia: 6 giorni, niente rimborso.
        assertThat(RefundRule.refundDue(FEE, START, SeatPosition.IN_SEATS, at("2027-07-13T22:30:00Z"))).isFalse();
        assertThat(RefundRule.refundDue(FEE, START, SeatPosition.IN_SEATS, at("2027-07-13T21:59:00Z"))).isTrue();
    }

    @Test
    void noFeeOrWaitingMeansNoRefund() {
        assertThat(RefundRule.refundDue(BigDecimal.ZERO, START, SeatPosition.IN_SEATS, at("2027-06-01T10:00:00Z"))).isFalse();
        assertThat(RefundRule.refundDue(null, START, SeatPosition.IN_SEATS, at("2027-06-01T10:00:00Z"))).isFalse();
        assertThat(RefundRule.refundDue(FEE, START, SeatPosition.WAITING, at("2027-06-01T10:00:00Z"))).isFalse();
    }

    private static Instant at(String instant) {
        return Instant.parse(instant);
    }
}
