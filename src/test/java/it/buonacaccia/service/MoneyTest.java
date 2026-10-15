package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Importi in centesimi (research R1 della 005), senza database. */
class MoneyTest {

    @Test
    void euroConDueDecimaliDiventanoCentesimiInteri() {
        assertThat(Money.toCents(new BigDecimal("150"))).isEqualTo(15000);
        assertThat(Money.toCents(new BigDecimal("12.5"))).isEqualTo(1250);
        assertThat(Money.toCents(new BigDecimal("0.01"))).isEqualTo(1);
    }

    @Test
    void importiNonPositiviOConPiuDiDueDecimaliSonoRifiutati() {
        assertThatThrownBy(() -> Money.toCents(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.toCents(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.toCents(new BigDecimal("1.005"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("due decimali");
        assertThatThrownBy(() -> Money.toCents(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laQuotaAssenteOZeroVale0() {
        assertThat(Money.feeToCents(null)).isZero();
        assertThat(Money.feeToCents(BigDecimal.ZERO)).isZero();
        assertThat(Money.feeToCents(new BigDecimal("150.00"))).isEqualTo(15000);
    }

    @Test
    void ICentesimiTornanoEuroConScalaDue() {
        assertThat(Money.toEuro(15000)).isEqualByComparingTo("150.00").hasScaleOf(2);
        assertThat(Money.toEuro(5).toPlainString()).isEqualTo("0.05");
    }
}
