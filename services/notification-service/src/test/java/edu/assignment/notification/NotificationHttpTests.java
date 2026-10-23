package edu.assignment.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.assignment.notification.repository.NotificationEventRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationHttpTests {
    @TempDir static Path tempDir;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("notification-http-test.db"));
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired NotificationEventRepository events;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

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
        assertThat(events.countByAppointmentId(102L)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateSubmissionsCreateOnePersistedEvent() throws Exception {
        int callers = 20;
        long appointmentId = 103;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        HttpClient client = HttpClient.newHttpClient();
        String json = "{\"appointmentId\":103,\"patientId\":7,\"slotId\":9,"
                + "\"reservationId\":\"reservation-103\",\"createdAt\":\"2026-10-17T12:00:00Z\"}";
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("notification start gate timed out");
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notifications"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build();
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) responses.add(future.get(30, TimeUnit.SECONDS));
            assertThat(responses).hasSize(callers).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(202));
            List<String> notificationIds = new ArrayList<>();
            List<String> acceptedAt = new ArrayList<>();
            for (HttpResponse<String> response : responses) {
                JsonNode body = mapper.readTree(response.body());
                notificationIds.add(body.path("notificationId").asText());
                acceptedAt.add(body.path("acceptedAt").asText());
            }
            assertThat(notificationIds).hasSize(callers).containsOnly(notificationIds.get(0));
            assertThat(acceptedAt).hasSize(callers).containsOnly(acceptedAt.get(0));
            assertThat(events.countByAppointmentId(appointmentId)).isEqualTo(1);
            System.out.printf("NOTIFICATION_CONCURRENCY_RESULT timestamp=%s requests=%d accepted=%d eventRows=1 sameEventId=true result=PASS%n",
                    java.time.Instant.now(), callers, responses.size());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void healthEndpointRemainsAvailable() {
        ResponseEntity<JsonNode> response = http.getForEntity("http://localhost:" + port + "/health", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }

    @Test
    void sqliteBusyTimeoutIsAppliedToPooledConnections() {
        assertThat(jdbc.queryForObject("PRAGMA busy_timeout", Integer.class)).isEqualTo(5000);
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
