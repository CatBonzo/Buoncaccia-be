package it.buonacaccia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base dei test di integrazione, come l'AbstractTest di ScoutShopper-be: crea il database
 * (MongoDB in Testcontainers, stessa immagine di compose.yaml, uno solo per tutta la suite),
 * avvia l'applicazione vera su porta casuale con il profilo {@code mocked} e offre un client
 * HTTP che restituisce status e corpo JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mocked")
public abstract class AbstractTest {

    @ServiceConnection
    protected static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0.32"));

    static {
        MONGO.start();
    }

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    protected record Result(int status, JsonNode body) {
    }

    protected Result get(String path) {
        return get(path, null);
    }

    protected Result get(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean json = response.headers().firstValue("Content-Type").orElse("").contains("json");
            JsonNode body = json && !response.body().isBlank() ? JSON.readTree(response.body()) : null;
            return new Result(response.statusCode(), body);
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
