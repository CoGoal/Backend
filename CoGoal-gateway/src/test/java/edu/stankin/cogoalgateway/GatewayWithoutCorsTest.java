package edu.stankin.cogoalgateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CORS_ALLOWED_ORIGINS is not set (the default): the gateway starts, plain requests pass, cross-origin ones do not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
class GatewayWithoutCorsTest {

    private static final StubService MAIN = new StubService("main", Map.of());

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances[COGOAL-MAIN][0].uri", MAIN::uri);
    }

    @AfterAll
    static void stopStub() {
        MAIN.close();
    }

    @Test
    void requestsWithoutOriginPass() throws Exception {
        HttpResponse<String> response = send(request().GET().build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void crossOriginRequestsAreRejected() throws Exception {
        HttpResponse<String> response = send(request().header("Origin", "http://front.example").GET().build());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpRequest.Builder request() {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/users/me"));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
