package edu.stankin.cogoaleureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoGoalEurekaApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void registryAndHealthAreServed() {
        RestClient client = RestClient.create("http://localhost:" + port);

        String apps = client.get().uri("/eureka/apps").header("Accept", "application/json").retrieve().body(String.class);
        String health = client.get().uri("/actuator/health").retrieve().body(String.class);

        assertThat(apps).contains("applications");
        assertThat(health).contains("\"UP\"");
    }
}
