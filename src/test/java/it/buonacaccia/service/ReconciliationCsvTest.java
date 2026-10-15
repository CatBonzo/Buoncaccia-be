package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Formato del CSV di riconciliazione (research R11 della 005), senza database. */
class ReconciliationCsvTest {

    private static final ReconciliationCsv.Row PAYMENT = new ReconciliationCsv.Row("PAGAMENTO", "PIE-1-0001",
            "Rocco Rover", new BigDecimal("150"), "BANK_TRANSFER", "VALID", LocalDate.of(2026, 10, 5), "CRO123", "u-segreteria");

    @Test
    void ILCsvHaLIntestazioneEUnaRigaPerVoce() {
        ReconciliationCsv.Row refund = new ReconciliationCsv.Row("RIMBORSO", "PIE-1-0001", "Rocco Rover",
                new BigDecimal("150.5"), null, "ISSUED", LocalDate.of(2026, 11, 2), null, "u-segreteria");

        List<String> lines = ReconciliationCsv.render(List.of(PAYMENT, refund)).lines().toList();

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("tipo;iscrizione;partecipante;importo;metodo;stato;data;riferimento;registrato_da");
        assertThat(lines.get(1)).isEqualTo("PAGAMENTO;PIE-1-0001;Rocco Rover;150.00;BANK_TRANSFER;VALID;05/10/2026;CRO123;u-segreteria");
        assertThat(lines.get(2)).isEqualTo("RIMBORSO;PIE-1-0001;Rocco Rover;150.50;;ISSUED;02/11/2026;;u-segreteria");
    }

    @Test
    void ICampiConPuntoEVirgolaVirgoletteOACapoVannoFraVirgolette() {
        ReconciliationCsv.Row tricky = new ReconciliationCsv.Row("PAGAMENTO", "N-1", "Anna; \"la Rossa\"",
                new BigDecimal("1"), "OFFICE_CASH", "VALID", LocalDate.of(2026, 1, 9), "riga\nuno", "u1");

        String csv = ReconciliationCsv.render(List.of(tricky));

        assertThat(csv).contains("\"Anna; \"\"la Rossa\"\"\"").contains("\"riga\nuno\"").contains(";1.00;");
    }

    @Test
    void senzaVociCESoloLIntestazione() {
        assertThat(ReconciliationCsv.render(List.of()).lines().toList()).hasSize(1);
    }
}
