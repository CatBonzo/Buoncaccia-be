package it.buonacaccia.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV di riconciliazione (research R11 della 005): separatore {@code ;} per l'Excel italiano, importi
 * con il punto e due decimali, date {@code dd/MM/yyyy}, campi fra virgolette se contengono {@code ;},
 * {@code "} o un a capo. Scritto a mano: sono nove colonne di testo.
 */
public final class ReconciliationCsv {

    private static final String HEADER = "tipo;iscrizione;partecipante;importo;metodo;stato;data;riferimento;registrato_da";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ReconciliationCsv() {
    }

    /** Una riga: un pagamento o un rimborso. */
    public record Row(String type, String enrollmentNumber, String participantName, BigDecimal amount, String method,
                      String status, LocalDate date, String reference, String by) {
    }

    public static String render(List<Row> rows) {
        return HEADER + "\r\n" + rows.stream().map(ReconciliationCsv::line).collect(Collectors.joining());
    }

    private static String line(Row row) {
        return String.join(";", field(row.type()), field(row.enrollmentNumber()), field(row.participantName()),
                row.amount() == null ? "" : row.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                field(row.method()), field(row.status()), row.date() == null ? "" : DATE.format(row.date()),
                field(row.reference()), field(row.by())) + "\r\n";
    }

    private static String field(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return quote ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
