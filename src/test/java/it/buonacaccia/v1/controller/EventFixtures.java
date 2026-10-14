package it.buonacaccia.v1.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Corpi JSON di prova per gli eventi: un evento completo e pubblicabile, da modificare nei test. */
final class EventFixtures {

    private EventFixtures() {
    }

    /** Evento regionale completo, con un titolo unico per ritrovarlo nella ricerca. */
    static Map<String, Object> validEvent(String region, String year) {
        Map<String, Object> event = new HashMap<>();
        event.put("typeCode", "ROSS");
        event.put("title", "Route di prova " + UUID.randomUUID());
        event.put("description", "Una settimana di cammino e servizio.");
        event.put("level", "REGIONAL");
        event.put("region", region);
        event.put("location", "Val Pellice");
        event.put("province", "TO");
        event.put("registrationOpenDate", year + "-03-01");
        event.put("registrationCloseDate", year + "-06-30");
        event.put("startDate", year + "-07-20");
        event.put("endDate", year + "-07-27");
        event.put("seats", 40);
        event.put("fee", 150.00);
        event.put("enrollmentMode", "SELF");
        event.put("contacts", Map.of(
                "methodological", Map.of("name", "Marco", "email", "marco@example.org", "phone", "3330000000"),
                "administrative", Map.of("name", "Paola", "email", "paola@example.org")));
        return event;
    }

    static Map<String, Object> with(Map<String, Object> event, Object... keyValues) {
        Map<String, Object> copy = new HashMap<>(event);
        for (int i = 0; i < keyValues.length; i += 2) {
            copy.put((String) keyValues[i], keyValues[i + 1]);
        }
        return copy;
    }

    static Map<String, Object> version(long version) {
        return Map.of("version", version);
    }

    static List<String> fields(tools.jackson.databind.JsonNode payload) {
        return payload.valueStream().map(node -> node.get("field").asString()).toList();
    }
}
