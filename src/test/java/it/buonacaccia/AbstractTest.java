package it.buonacaccia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base dei test di integrazione, come l'AbstractTest di ScoutShopper-be: crea il database
 * (MongoDB in Testcontainers, stessa immagine di docker-compose.yaml, uno solo per tutta la suite),
 * Kafka e Mailpit (feature 004, research R11: le e-mail si leggono con {@link MailpitClient}),
 * avvia l'applicazione vera su porta casuale con il profilo {@code mocked} e offre un client
 * HTTP che restituisce status e corpo JSON. L'ora dell'applicazione è un {@link MutableClock}:
 * segue l'ora vera salvo nei test che la fermano, e torna all'ora vera dopo ogni test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mocked")
@Import(TestClockConfig.class)
public abstract class AbstractTest {

    @ServiceConnection
    protected static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.32"))
            .withReplicaSet();

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    protected static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.5"))
                    .withExposedPorts(1025, 8025)
                    .waitingFor(Wait.forHttp("/readyz").forPort(8025));

    static {
        MONGO.start();
        KAFKA.start();
        MAILPIT.start();
    }

    /** Posta su Mailpit; relay e ritentativi più rapidi che in produzione, per non allungare la suite. */
    @DynamicPropertySource
    static void notificationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("buonacaccia.notifications.relay-delay", () -> "PT0.2S");
        registry.add("buonacaccia.notifications.retry.initial-interval", () -> "200ms");
        registry.add("buonacaccia.notifications.retry.multiplier", () -> "2");
        registry.add("buonacaccia.notifications.retry.max-attempts", () -> "4");
    }

    protected static final MailpitClient MAIL = new MailpitClient(
            "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    protected MutableClock clock;

    @Value("${local.server.port}")
    private int port;

    protected record Result(int status, JsonNode body) {
    }

    protected Result get(String path) {
        return get(path, null);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    protected Result get(String path, String token) {
        return send("GET", path, token, null);
    }

    protected Result post(String path, String token, Object body) {
        return send("POST", path, token, body);
    }

    protected Result put(String path, String token, Object body) {
        return send("PUT", path, token, body);
    }

    /** Richiesta HTTP con corpo JSON facoltativo (serializzato da {@code body}). */
    protected Result send(String method, String path, String token, Object body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, publisher);
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean json = response.headers().firstValue("Content-Type").orElse("").contains("json");
            JsonNode responseBody = json && !response.body().isBlank() ? JSON.readTree(response.body()) : null;
            return new Result(response.statusCode(), responseBody);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Risposta con il corpo grezzo, per i contenuti scaricati (file). */
    protected record RawResult(int status, byte[] body, String contentType, String disposition) {
    }

    /** POST multipart con un solo file nella parte {@code file}. */
    protected Result postFile(String path, String token, String filename, String declaredType, byte[] content) {
        String boundary = "----test" + java.util.UUID.randomUUID();
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + declaredType + "\r\n\r\n";
        body.writeBytes(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean json = response.headers().firstValue("Content-Type").orElse("").contains("json");
            return new Result(response.statusCode(), json && !response.body().isBlank() ? JSON.readTree(response.body()) : null);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** GET che restituisce i byte e le intestazioni del file scaricato. */
    protected RawResult getBytes(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new RawResult(response.statusCode(), response.body(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.headers().firstValue("Content-Disposition").orElse(""));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** GET con la risposta intera (intestazioni e testo), per i contenuti che non sono JSON come il CSV. */
    protected HttpResponse<String> getRaw(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token dell'utente di prova con lo username indicato, preso da findAll. */
    protected String tokenOf(String username) {
        for (JsonNode user : get("/api/v1/mock/auth/findAll").body().get("payload")) {
            if (username.equals(user.get("username").asString())) {
                return user.get("accessToken").asString();
            }
        }
        throw new IllegalArgumentException("Utente di prova sconosciuto: " + username);
    }
}
