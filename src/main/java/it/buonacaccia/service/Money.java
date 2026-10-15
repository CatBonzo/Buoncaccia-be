package it.buonacaccia.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importi in euro ↔ centesimi interi (research R1 della 005). Nel database e nei calcoli gli importi
 * sono {@code long} di centesimi; alle API arrivano e partono euro con due decimali.
 */
public final class Money {

    private Money() {
    }

    /**
     * @throws IllegalArgumentException se l'importo non è maggiore di 0 o ha più di due decimali
     */
    public static long toCents(BigDecimal euro) {
        if (euro == null || euro.signum() <= 0) {
            throw new IllegalArgumentException("L'importo deve essere maggiore di 0.");
        }
        try {
            return euro.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("L'importo ha al massimo due decimali.");
        }
    }

    /** Quota dell'evento in centesimi; una quota assente o a zero vale 0. */
    public static long feeToCents(BigDecimal fee) {
        return fee == null || fee.signum() <= 0 ? 0 : fee.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValue();
    }

    public static BigDecimal toEuro(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
