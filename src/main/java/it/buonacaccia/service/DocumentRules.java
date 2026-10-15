package it.buonacaccia.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.buonacaccia.model.DocumentDelivery;
import it.buonacaccia.model.Obligation;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.v1.dto.FieldErrorDTO;

/** Valori predefiniti e controlli sui documenti richiesti dall'evento (FR-001, FR-003 della 006). */
public final class DocumentRules {

    public static final String DEFAULT_CODE = "DOC";
    public static final List<String> SUPPORTED_TYPES = List.of("image/jpeg", "image/png", "application/pdf");
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    public static final long LIMIT_BYTES = 10L * 1024 * 1024;

    private DocumentRules() {
    }

    public static String codeOf(String code) {
        return code == null || code.isBlank() ? DEFAULT_CODE : code;
    }

    public static DocumentDelivery deliveryOf(DocumentDelivery delivery) {
        return delivery == null ? DocumentDelivery.ONLINE : delivery;
    }

    public static List<String> typesOf(List<String> types) {
        return types == null || types.isEmpty() ? SUPPORTED_TYPES : types;
    }

    public static long maxBytesOf(Long maxBytes) {
        return maxBytes == null ? DEFAULT_MAX_BYTES : maxBytes;
    }

    /** Codici duplicati fra i documenti dell'evento e tipi che non sappiamo riconoscere. */
    public static List<FieldErrorDTO> validate(List<Obligation> obligations) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        if (obligations == null) {
            return errors;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < obligations.size(); i++) {
            Obligation obligation = obligations.get(i);
            if (obligation.getType() != ObligationType.DOCUMENT) {
                continue;
            }
            if (!seen.add(codeOf(obligation.getCode()))) {
                errors.add(new FieldErrorDTO("obligations[" + i + "].code", "Il codice del documento è già usato in questo evento."));
            }
            for (String type : typesOf(obligation.getAllowedMimeTypes())) {
                if (!SUPPORTED_TYPES.contains(type)) {
                    errors.add(new FieldErrorDTO("obligations[" + i + "].allowedMimeTypes",
                            "Tipo non supportato: " + type + ". Ammessi: " + String.join(", ", SUPPORTED_TYPES) + "."));
                }
            }
        }
        return errors;
    }
}
