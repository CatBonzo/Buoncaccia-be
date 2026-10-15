package it.buonacaccia.service;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;

/**
 * Controllo formale dell'IBAN (research R10 della 005): lunghezza del paese e modulo 97. Non verifica
 * che il conto esista. I paesi sono quelli europei più comuni.
 */
public final class Iban {

    private static final Map<String, Integer> LENGTH = Map.ofEntries(
            Map.entry("AT", 20), Map.entry("BE", 16), Map.entry("CH", 21), Map.entry("CY", 28),
            Map.entry("CZ", 24), Map.entry("DE", 22), Map.entry("DK", 18), Map.entry("ES", 24),
            Map.entry("FI", 18), Map.entry("FR", 27), Map.entry("GB", 22), Map.entry("GR", 27),
            Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22), Map.entry("IT", 27),
            Map.entry("LU", 20), Map.entry("MT", 31), Map.entry("NL", 18), Map.entry("NO", 15),
            Map.entry("PL", 28), Map.entry("PT", 25), Map.entry("RO", 24), Map.entry("SE", 24),
            Map.entry("SI", 19), Map.entry("SK", 24), Map.entry("SM", 27), Map.entry("VA", 22));

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** Maiuscolo e senza spazi. */
    public static String normalize(String iban) {
        return iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String iban) {
        String value = normalize(iban);
        if (value == null || value.length() < 5 || !value.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]+")) {
            return false;
        }
        Integer expected = LENGTH.get(value.substring(0, 2));
        if (expected == null || expected != value.length()) {
            return false;
        }
        String rearranged = value.substring(4) + value.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
