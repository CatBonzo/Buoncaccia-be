package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/** Il frontend in sviluppo (localhost:4200) può chiamare le API dal browser; le altre origini no. */
class CorsTest extends AbstractTest {

    private static final String FRONTEND = "http://localhost:4200";

    @Value("${local.server.port}")
    private int serverPort;

    private HttpResponse<String> call(String method, String path, String origin, String requestMethod) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin);
        if (requestMethod != null) {
            request.header("Access-Control-Request-Method", requestMethod)
                    .header("Access-Control-Request-Headers", "authorization,content-type");
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void ilPreflightDelFrontendEAccettatoConIntestazioniEMetodi() throws Exception {
        HttpResponse<String> response = call("OPTIONS", "/api/v1/enrollments/mine", FRONTEND, "PUT");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(FRONTEND);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("PUT").contains("POST");
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
                .contains("authorization").contains("content-type");
    }

    @Test
    void lePubblicheRispondonoConLOriginePermessaEIFileScaricabiliEspongonoIlNome() throws Exception {
        HttpResponse<String> response = call("GET", "/api/v1/events", FRONTEND, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(FRONTEND);
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers").orElse("")).contains("Content-Disposition");
    }

    @Test
    void unAltraOrigineNonVieneAccettata() throws Exception {
        HttpResponse<String> preflight = call("OPTIONS", "/api/v1/enrollments/mine", "http://altro.example", "GET");
        HttpResponse<String> simple = call("GET", "/api/v1/events", "http://altro.example", null);

        assertThat(preflight.statusCode()).isEqualTo(403);
        assertThat(simple.statusCode()).isEqualTo(403);
        assertThat(simple.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }
}
