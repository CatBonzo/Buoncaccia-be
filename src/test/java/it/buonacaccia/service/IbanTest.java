package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Validazione dell'IBAN (research R10 della 005), senza database. */
class IbanTest {

    @Test
    void unIbanItalianoValidoPassa() {
        assertThat(Iban.isValid("IT60X0542811101000000123456")).isTrue();
        assertThat(Iban.isValid("DE89370400440532013000")).isTrue();
    }

    @Test
    void spaziEMinuscoleSonoNormalizzati() {
        assertThat(Iban.normalize(" it60 x054 2811 1010 0000 0123 456 ")).isEqualTo("IT60X0542811101000000123456");
        assertThat(Iban.isValid("it60 x054 2811 1010 0000 0123 456")).isTrue();
    }

    @Test
    void ICaratteriDiControlloErratiSonoRifiutati() {
        assertThat(Iban.isValid("IT61X0542811101000000123456")).isFalse();
    }

    @Test
    void laLunghezzaDeveEssereQuellaDelPaese() {
        assertThat(Iban.isValid("IT60X054281110100000012345")).isFalse();
        assertThat(Iban.isValid("IT60X05428111010000001234567")).isFalse();
    }

    @Test
    void caratteriNonAmmessiEPaeseSconosciutoSonoRifiutati() {
        assertThat(Iban.isValid("IT60X0542811101000000123-56")).isFalse();
        assertThat(Iban.isValid("XX00123456789012345678")).isFalse();
        assertThat(Iban.isValid("")).isFalse();
        assertThat(Iban.isValid(null)).isFalse();
    }
}
