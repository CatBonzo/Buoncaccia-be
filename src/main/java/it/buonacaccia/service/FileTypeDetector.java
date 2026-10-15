package it.buonacaccia.service;

import java.util.Optional;

/**
 * Riconosce il tipo reale di un file dai primi byte (research R2 della 006): JPEG, PNG e PDF.
 * Il nome e il tipo dichiarati dal client non contano.
 */
public final class FileTypeDetector {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    private FileTypeDetector() {
    }

    public static Optional<String> detect(byte[] head) {
        if (head.length >= 3 && head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(head, PNG)) {
            return Optional.of("image/png");
        }
        if (startsWith(head, PDF)) {
            return Optional.of("application/pdf");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
