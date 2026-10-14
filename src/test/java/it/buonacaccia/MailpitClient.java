package it.buonacaccia;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.awaitility.Awaitility;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Legge la casella di prova di Mailpit tramite la sua API REST (feature 004, research R11). Kafka e la
 * casella sono condivisi da tutta la suite: i test cercano le e-mail per destinatario e per un testo
 * dell'oggetto proprio del test (numero dell'iscrizione o codice dell'evento), non contano tutto.
 */
public class MailpitClient {

    /** E-mail ricevuta: destinatario, oggetto e testo. */
    public record Mail(String to, String subject, String text) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    public MailpitClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Tutte le e-mail per {@code to} il cui oggetto contiene {@code subjectPart}. */
    public List<Mail> find(String to, String subjectPart) {
        String query = URLEncoder.encode("to:" + to, StandardCharsets.UTF_8);
        JsonNode found = request("GET", "/api/v1/search?limit=500&query=" + query);
        List<Mail> result = new ArrayList<>();
        for (JsonNode message : found.get("messages")) {
            String subject = message.get("Subject").asString();
            if (subject.contains(subjectPart)) {
                String text = request("GET", "/api/v1/message/" + message.get("ID").asString()).get("Text").asString();
                result.add(new Mail(to, subject, text));
            }
        }
        return result;
    }

    /** Aspetta che arrivino esattamente {@code count} e-mail che rispettano i criteri, e le restituisce. */
    public List<Mail> expect(String to, String subjectPart, int count) {
        Awaitility.await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200))
                .until(() -> find(to, subjectPart).size() >= count);
        List<Mail> mails = find(to, subjectPart);
        if (mails.size() != count) {
            throw new AssertionError("Attese " + count + " e-mail a " + to + " con \"" + subjectPart + "\", arrivate "
                    + mails.size() + ": " + mails.stream().map(Mail::subject).toList());
        }
        return mails;
    }

    /** Una sola e-mail. */
    public Mail expectOne(String to, String subjectPart) {
        return expect(to, subjectPart, 1).getFirst();
    }

    /** Controlla per un po' che nessuna e-mail (o non più di {@code count}) arrivi. */
    public void assertStays(String to, String subjectPart, int count, Duration during) {
        Awaitility.await().during(during).atMost(during.plusSeconds(5)).pollInterval(Duration.ofMillis(250))
                .until(() -> find(to, subjectPart).size() == count);
    }

    private JsonNode request(String method, String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body().isBlank() ? null : JSON.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
