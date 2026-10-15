package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import it.buonacaccia.model.Obligation;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import org.junit.jupiter.api.Test;

/** Valori predefiniti e controlli dei documenti richiesti (FR-001, FR-003 della 006). */
class DocumentRulesTest {

    private static Obligation doc(String code, String... types) {
        return Obligation.builder().type(ObligationType.DOCUMENT).code(code).allowedMimeTypes(types.length == 0 ? null : List.of(types)).build();
    }

    @Test
    void iDefaultSonoJpegPngPdfCinqueMegaEOnlineConCodiceDoc() {
        assertThat(DocumentRules.codeOf(null)).isEqualTo("DOC");
        assertThat(DocumentRules.typesOf(null)).containsExactly("image/jpeg", "image/png", "application/pdf");
        assertThat(DocumentRules.maxBytesOf(null)).isEqualTo(5L * 1024 * 1024);
        assertThat(DocumentRules.deliveryOf(null).name()).isEqualTo("ONLINE");
    }

    @Test
    void codiciDuplicatiESenzaCodiceDuplicatoSonoErrori() {
        List<FieldErrorDTO> errors = DocumentRules.validate(List.of(doc("FOTO"), doc("FOTO"), doc(null), doc(null)));

        assertThat(errors).extracting(FieldErrorDTO::getField).containsExactly("obligations[1].code", "obligations[3].code");
    }

    @Test
    void unTipoNonSupportatoEUnErroreSulCampo() {
        assertThat(DocumentRules.validate(List.of(doc("A", "application/x-msdownload"))))
                .extracting(FieldErrorDTO::getField).containsExactly("obligations[0].allowedMimeTypes");
        assertThat(DocumentRules.validate(List.of(doc("A", "image/png")))).isEmpty();
    }

    @Test
    void glialtriTipiDiAdempimentoNonSiControllano() {
        assertThat(DocumentRules.validate(List.of(Obligation.builder().type(ObligationType.PAYMENT).code("X").build(),
                Obligation.builder().type(ObligationType.PAYMENT).code("X").build()))).isEmpty();
    }
}
