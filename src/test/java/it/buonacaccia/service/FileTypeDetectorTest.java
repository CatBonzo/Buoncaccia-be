package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Tipo reale dai primi byte (research R2 della 006), senza database. */
class FileTypeDetectorTest {

    @Test
    void riconosceJpegPngEPdf() {
        assertThat(FileTypeDetector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10}))
                .contains("image/jpeg");
        assertThat(FileTypeDetector.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}))
                .contains("image/png");
        assertThat(FileTypeDetector.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains("application/pdf");
    }

    @Test
    void ilRestoNonELoRiconosciuto() {
        assertThat(FileTypeDetector.detect("MZ eseguibile".getBytes(StandardCharsets.US_ASCII))).isEqualTo(Optional.empty());
        assertThat(FileTypeDetector.detect("<html>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }
}
