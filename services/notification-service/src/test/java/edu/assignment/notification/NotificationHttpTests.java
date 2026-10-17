package edu.assignment.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationHttpTests {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    @BeforeEach
    void resetEvents() {
        // Each test uses a distinct appointment identifier; the service intentionally models process-local events.
    }

    @Test
    void acceptsNotificationAndReturnsRecordedEvent() {
        ResponseEntity<JsonNode> response = send(validPayload(101));
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().path("appointmentId").asLong()).isEqualTo(101);
        assertThat(response.getBody().path("notificationId").asText()).startsWith("notification-");
        assertThat(response.getBody().path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(response.getBody().path("acceptedAt").asText()).isNotBlank();
    }

    @Test
    void rejectsInvalidPayloadWithClearBadRequest() {
        ResponseEntity<JsonNode> response = send(Map.of("appointmentId", 0, "patientId", -1));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().path("code").asText()).isEqualTo("INVALID_NOTIFICATION");
        assertThat(response.getBody().path("message").asText()).isNotBlank();
    }

    @Test
    void duplicateAppointmentReturnsOriginalEvent() {
        ResponseEntity<JsonNode> first = send(validPayload(102));
        ResponseEntity<JsonNode> duplicate = send(validPayload(102));
        assertThat(duplicate.getStatusCode().value()).isEqualTo(202);
        assertThat(duplicate.getBody().path("notificationId").asText())
                .isEqualTo(first.getBody().path("notificationId").asText());
        assertThat(duplicate.getBody().path("acceptedAt").asText())
                .isEqualTo(first.getBody().path("acceptedAt").asText());
    }

    @Test
    void healthEndpointRemainsAvailable() {
        ResponseEntity<JsonNode> response = http.getForEntity("http://localhost:" + port + "/health", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }

    private ResponseEntity<JsonNode> send(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/notifications", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static Map<String, Object> validPayload(long appointmentId) {
        return Map.of("appointmentId", appointmentId, "patientId", 7, "slotId", 9,
                "reservationId", "reservation-" + appointmentId, "createdAt", "2026-10-17T12:00:00Z");
    }
}
