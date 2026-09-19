package io.bidvelocity.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real integration test: boots the Eureka server on a random port and
 * verifies discovery endpoints + actuator health respond.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void contextLoadsAndEurekaEndpointsRespond() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(health.getBody()).contains("UP");

        // the Eureka REST registry must answer (empty registry is valid at boot)
        ResponseEntity<String> apps = rest.getForEntity("/eureka/apps", String.class);
        assertThat(apps.getStatusCode().value()).isIn(200, 404); // 404 = no registrations yet
    }
}
