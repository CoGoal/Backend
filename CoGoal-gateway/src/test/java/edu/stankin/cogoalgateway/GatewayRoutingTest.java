package edu.stankin.cogoalgateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real routes of application.yml against two stub services. Eureka is off; the stubs are registered
 * under the Eureka names (COGOAL-MAIN, COGOAL-AUTH) in the simple discovery client, so lb:// resolves the same way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "CORS_ALLOWED_ORIGINS=" + GatewayRoutingTest.ORIGIN
})
class GatewayRoutingTest {

    static final String ORIGIN = "http://front.example";

    private static final StubService MAIN = new StubService("main", Map.of());
    // auth answers with its own CORS header, as it would if CORS_ALLOWED_ORIGINS were set for it as well
    private static final StubService AUTH = new StubService("auth",
            Map.of("Access-Control-Allow-Origin", List.of(ORIGIN)));

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances[COGOAL-MAIN][0].uri", MAIN::uri);
        registry.add("spring.cloud.discovery.client.simple.instances[COGOAL-AUTH][0].uri", AUTH::uri);
    }

    @AfterAll
    static void stopStubs() {
        MAIN.close();
        AUTH.close();
    }

    @BeforeEach
    void clearStubs() {
        MAIN.received().clear();
        AUTH.received().clear();
    }

    @Test
    void authPathsGoToAuth() throws Exception {
        HttpResponse<String> response = send(post("/api/v1/auth/login").build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("auth");
        assertThat(AUTH.last().path()).isEqualTo("/api/v1/auth/login");
        assertThat(MAIN.received()).isEmpty();
    }

    @Test
    void otherApiPathsGoToMainWithAuthorizationUnchangedAndWithoutInternalToken() throws Exception {
        HttpResponse<String> response = send(request("/api/v1/users/me")
                .header("Authorization", "Bearer abc.def.ghi")
                .header("X-Internal-Token", "guessed-token")
                .GET().build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("main");
        assertThat(MAIN.last().path()).isEqualTo("/api/v1/users/me");
        assertThat(MAIN.last().headers().getFirst("Authorization")).isEqualTo("Bearer abc.def.ghi");
        assertThat(MAIN.last().headers().containsKey("X-internal-token")).isFalse();
        assertThat(AUTH.received()).isEmpty();
    }

    @Test
    void internalApiIsNotReachable() throws Exception {
        for (String path : List.of("/internal/users", "/internal/payments/events", "/main/internal/users",
                "/api/internal/users", "/main/v3/api-docs/../../internal/users",
                "/main/v3/api-docs/%2e%2e/%2e%2e/internal/users", "/api/v1/../../internal/users",
                "/api/v1/..;/..;/internal/users", "/api/v1/%2F..%2F..%2Finternal/users")) {
            HttpResponse<String> response = send(request(path).header("X-Internal-Token", "guessed-token")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build());

            assertThat(response.statusCode()).as(path).isIn(400, 404);
        }
        assertThat(MAIN.received()).isEmpty();
        assertThat(AUTH.received()).isEmpty();
    }

    @Test
    void apiDocsAreRewrittenAndKeepTheGatewayHost() throws Exception {
        assertThat(send(request("/main/v3/api-docs").GET().build()).body()).isEqualTo("main");
        assertThat(MAIN.last().path()).isEqualTo("/v3/api-docs");
        assertThat(MAIN.last().headers().getFirst("Host")).isEqualTo("localhost:" + port);

        assertThat(send(request("/auth/v3/api-docs").GET().build()).body()).isEqualTo("auth");
        assertThat(AUTH.last().path()).isEqualTo("/v3/api-docs");
    }

    @Test
    void multipartProofOfTwelveMegabytesIsPassedThrough() throws Exception {
        String boundary = "cogoal-boundary";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"proof.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[12 * 1024 * 1024 - head.length - tail.length];
        Arrays.fill(file, (byte) 'x');

        HttpResponse<String> response = send(request("/api/v1/check-ins/1/proofs")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, file, tail))).build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(MAIN.last().bodyLength()).isEqualTo(12L * 1024 * 1024);
    }

    @Test
    void corsPreflightIsAnsweredByTheGateway() throws Exception {
        HttpResponse<String> response = send(request("/api/v1/auth/login")
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ORIGIN);
        assertThat(AUTH.received()).isEmpty();
    }

    @Test
    void corsHeaderIsNotDuplicatedWhenTheServiceAlsoSendsIt() throws Exception {
        HttpResponse<String> response = send(post("/api/v1/auth/login").header("Origin", ORIGIN).build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ORIGIN);
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        HttpResponse<String> response = send(post("/api/v1/auth/login").header("Origin", "http://evil.example").build());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(AUTH.received()).isEmpty();
    }

    private HttpRequest.Builder post(String path) {
        return request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
    }

    @Test
    void profileChangesGoToMainAndLoginChangesGoToAuth() throws Exception {
        assertThat(send(request("/api/v1/users/me").method("PATCH", HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json").build()).body()).isEqualTo("main");
        assertThat(send(request("/api/v1/auth/me/username").method("PATCH", HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json").build()).body()).isEqualTo("auth");
        assertThat(send(post("/api/v1/users/me/email-change").build()).body()).isEqualTo("auth");
        assertThat(send(request("/api/v1/users/me").GET().build()).body()).isEqualTo("main");
    }

    @Test
    void paymentPreflightAllowsIdempotencyKey() throws Exception {
        var response=send(request("/api/v1/pacts/example/deposits/me/payment")
                .header("Origin", ORIGIN).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase()).contains("idempotency-key");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
