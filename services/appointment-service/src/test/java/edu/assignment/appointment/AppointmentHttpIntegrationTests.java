package edu.assignment.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.assignment.appointment.repository.AppointmentRepository;
import edu.assignment.appointment.dto.BookingRequest;
import edu.assignment.appointment.service.BookingRequestFingerprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppointmentHttpIntegrationTests {
    private static final MockWebServer patientServer = new MockWebServer();
    private static final MockWebServer availabilityServer = new MockWebServer();
    private static final MockWebServer notificationServer = new MockWebServer();
    private static final AtomicInteger notificationRequests = new AtomicInteger();
    private static final AtomicBoolean notificationUnavailable = new AtomicBoolean(false);
    private static final AtomicBoolean notificationSlow = new AtomicBoolean(false);
    private static final AtomicBoolean notificationClientError = new AtomicBoolean(false);
    private static final AtomicInteger transientNotificationFailures = new AtomicInteger();
    private static final AtomicLong notificationDelayMillis = new AtomicLong(0);
    private static final AtomicBoolean patientEligible = new AtomicBoolean(true);
    private static final AtomicBoolean patientUnavailable = new AtomicBoolean(false);
    private static final AtomicBoolean slotUnavailable = new AtomicBoolean(false);
    private static final AtomicInteger reservationRequests = new AtomicInteger();
    private static final Path database = createTemporaryDatabase();

    static {
        try {
            patientServer.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    if (patientUnavailable.get()) return json(503, "{\"message\":\"patient service unavailable\"}");
                    if (request.getPath().matches("/api/patients/\\d+/eligibility")) {
                        boolean eligible = patientEligible.get();
                        return json(200, "{\"patientId\":1,\"exists\":true,\"active\":" + eligible + ",\"eligible\":" + eligible + "}");
                    }
                    return json(404, "{}");
                }
            });
            availabilityServer.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    if (request.getPath().matches("/api/v1/slots/\\d+/reservations")) {
                        reservationRequests.incrementAndGet();
                        if (slotUnavailable.get()) return json(409, "{\"message\":\"Slot capacity exhausted\"}");
                        return json(201, "{\"reservationId\":\"reservation-" + reservationRequests.get() + "\",\"slotId\":2,\"released\":false}");
                    }
                    if (request.getPath().matches("/api/v1/reservations/.+/release")) {
                        return json(200, "{\"reservationId\":\"released\",\"slotId\":2,\"released\":true}");
                    }
                    return json(404, "{}");
                }
            });
            notificationServer.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    notificationRequests.incrementAndGet();
                    if (notificationUnavailable.get()) return json(503, "{\"message\":\"notification unavailable\"}");
                    if (notificationClientError.get()) return json(400, "{\"message\":\"invalid notification\"}");
                    if (transientNotificationFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0)
                        return json(503, "{\"message\":\"temporary notification failure\"}");
                    long delay = notificationDelayMillis.get();
                    if (notificationSlow.get() && delay > 0) return json(202, "{\"notificationId\":\"slow\",\"status\":\"ACCEPTED\"}").setHeadersDelay(delay, TimeUnit.MILLISECONDS);
                    return json(202, "{\"notificationId\":\"notification-1\",\"status\":\"ACCEPTED\"}");
                }
            });
            patientServer.start();
            availabilityServer.start();
            notificationServer.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void downstreamAndDatabase(DynamicPropertyRegistry properties) {
        properties.add("appointment.clients.patient-url", () -> patientServer.url("/").toString());
        properties.add("appointment.clients.availability-url", () -> availabilityServer.url("/").toString());
        properties.add("appointment.clients.notification-url", () -> notificationServer.url("/").toString());
        properties.add("appointment.clients.notification-path", () -> "/api/notifications");
        properties.add("appointment.clients.connect-timeout", () -> "500ms");
        properties.add("appointment.clients.response-timeout", () -> "150ms");
        properties.add("appointment.clients.notification-retry.max-attempts", () -> "3");
        properties.add("appointment.clients.notification-retry.initial-delay", () -> "2ms");
        properties.add("appointment.clients.notification-retry.max-delay", () -> "4ms");
        properties.add("appointment.clients.notification-retry.jitter-cap", () -> "1ms");
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + database.toAbsolutePath());
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired AppointmentRepository appointments;

    @BeforeEach
    void resetState() throws InterruptedException {
        appointments.deleteAll();
        appointments.flush();
        while (notificationServer.takeRequest(0, TimeUnit.MILLISECONDS) != null) { /* drain requests left by prior tests */ }
        patientEligible.set(true);
        patientUnavailable.set(false);
        slotUnavailable.set(false);
        reservationRequests.set(0);
        notificationRequests.set(0);
        notificationUnavailable.set(false);
        notificationSlow.set(false);
        notificationClientError.set(false);
        transientNotificationFailures.set(0);
        notificationDelayMillis.set(0);
    }

    @AfterAll
    void cleanUp() throws IOException {
        patientServer.shutdown();
        availabilityServer.shutdown();
        notificationServer.shutdown();
        database.resolveSibling(database.getFileName() + "-wal").toFile().deleteOnExit();
        database.resolveSibling(database.getFileName() + "-shm").toFile().deleteOnExit();
        database.toFile().deleteOnExit();
        database.getParent().toFile().deleteOnExit();
    }

    @Test
    void successfulBookingReturnsCreatedAndPersistsAppointment() throws Exception {
        // Test-specific path enables local notification delivery.
        ResponseEntity<JsonNode> response = postWithNotification("success-key", 1, 2);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().path("notificationStatus").asText()).isEqualTo("SENT");
        assertThat(response.getBody().path("notificationAttempts").asInt()).isEqualTo(1);
        assertThat(notificationRequests.get()).isEqualTo(1);
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(reservationRequests.get()).isEqualTo(1);
        RecordedRequest notification = notificationServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(notification).isNotNull();
        assertThat(notification.getPath()).isEqualTo("/api/notifications");
        JsonNode payload = new ObjectMapper().readTree(notification.getBody().readUtf8());
        assertThat(payload.path("appointmentId").asLong()).isEqualTo(response.getBody().path("id").asLong());
        assertThat(payload.path("patientId").asLong()).isEqualTo(1);
        assertThat(payload.path("slotId").asLong()).isEqualTo(2);
        assertThat(payload.path("reservationId").asText()).isEqualTo("reservation-1");
        assertThat(payload.path("createdAt").asText()).isNotBlank();
    }

    @Test
    void transientNotificationFailureRetriesThenSucceeds() {
        transientNotificationFailures.set(1);

        ResponseEntity<JsonNode> response = postWithNotification("notification-retry-success", 1, 2);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().path("notificationStatus").asText()).isEqualTo("SENT");
        assertThat(response.getBody().path("notificationAttempts").asInt()).isEqualTo(2);
        assertThat(notificationRequests.get()).isEqualTo(2);
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(reservationRequests.get()).isEqualTo(1);
    }

    @Test
    void permanentNotificationClientErrorIsNotRetried() {
        notificationClientError.set(true);

        ResponseEntity<JsonNode> response = postWithNotification("notification-client-error", 1, 2);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().path("notificationStatus").asText()).isEqualTo("FAILED");
        assertThat(response.getBody().path("notificationAttempts").asInt()).isEqualTo(1);
        assertThat(notificationRequests.get()).isEqualTo(1);
        assertThat(appointments.count()).isEqualTo(1);
    }

    @Test
    void slowNotificationTimesOutAndExhaustsBoundedAttempts() {
        notificationSlow.set(true);
        notificationDelayMillis.set(600);

        ResponseEntity<JsonNode> response = postWithNotification("notification-timeout", 1, 2);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().path("notificationStatus").asText()).isEqualTo("FAILED");
        assertThat(response.getBody().path("notificationAttempts").asInt()).isEqualTo(3);
        assertThat(notificationRequests.get()).isEqualTo(3);
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(reservationRequests.get()).isEqualTo(1);

        notificationSlow.set(false);
        ResponseEntity<JsonNode> replay = postWithNotification("notification-timeout", 1, 2);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody().path("notificationStatus").asText()).isEqualTo("FAILED");
        assertThat(replay.getBody().path("notificationAttempts").asInt()).isEqualTo(3);
        assertThat(notificationRequests.get()).isEqualTo(3);
        assertThat(reservationRequests.get()).isEqualTo(1);
    }

    @Test
    void configuredNotificationFailureDoesNotFailBooking() {
        notificationUnavailable.set(true);
        ResponseEntity<JsonNode> response = postWithNotification("notification-fail", 1, 2);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().path("notificationStatus").asText()).isEqualTo("FAILED");
        assertThat(response.getBody().path("notificationAttempts").asInt()).isEqualTo(3);
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(reservationRequests.get()).isEqualTo(1);
        assertThat(notificationRequests.get()).isEqualTo(3);
    }

    @Test
    void retryDoesNotSendDuplicateNotification() {
        postWithNotification("notify-retry", 1, 2);
        ResponseEntity<JsonNode> retry = postWithNotification("notify-retry", 1, 2);
        assertThat(retry.getStatusCode().value()).isEqualTo(200);
        assertThat(retry.getBody().path("id").asLong()).isEqualTo(appointments.findByIdempotencyKey("notify-retry").orElseThrow().getId());
        assertThat(notificationRequests.get()).isEqualTo(1);
        assertThat(reservationRequests.get()).isEqualTo(1);
        assertThat(retry.getBody().path("notificationAttempts").asInt()).isEqualTo(1);
    }

    @Test
    void replayReturnsExistingAppointmentWithoutAnotherReservation() {
        ResponseEntity<JsonNode> first = post("replay-key", 1, 2);
        ResponseEntity<JsonNode> replay = post("replay-key", 1, 2);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody().path("id").asLong()).isEqualTo(first.getBody().path("id").asLong());
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(reservationRequests.get()).isEqualTo(1);
        assertThat(appointments.findByIdempotencyKey("replay-key").orElseThrow().getRequestFingerprint())
                .isEqualTo(BookingRequestFingerprint.of(new BookingRequest(1L, 2L)));
    }

    @Test
    void conflictingKeyReuseReturnsConflictWithoutCreatingAnotherAppointment() {
        post("conflict-key", 1, 2);

        ResponseEntity<JsonNode> conflict = post("conflict-key", 1, 3);

        assertError(conflict, 409, "IDEMPOTENCY_KEY_REUSED");
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(reservationRequests.get()).isEqualTo(1);
    }

    @Test
    void ineligiblePatientDoesNotReserveSlot() {
        patientEligible.set(false);

        ResponseEntity<JsonNode> response = post("ineligible-key", 1, 2);

        assertError(response, 422, "PATIENT_INELIGIBLE");
        assertThat(reservationRequests.get()).isZero();
        assertThat(appointments.count()).isZero();
    }

    @Test
    void unavailableSlotReturnsConflictWithoutPersistingAppointment() {
        slotUnavailable.set(true);

        ResponseEntity<JsonNode> response = post("full-slot-key", 1, 2);

        assertError(response, 409, "DOWNSTREAM_REJECTED");
        assertThat(appointments.count()).isZero();
        assertThat(reservationRequests.get()).isEqualTo(1);
    }

    @Test
    void patientServiceFailureReturnsGatewayErrorAndDoesNotReserveOrPersist() {
        patientUnavailable.set(true);

        ResponseEntity<JsonNode> response = post("patient-error-key", 1, 2);

        assertError(response, 502, "DOWNSTREAM_REJECTED");
        assertThat(reservationRequests.get()).isZero();
        assertThat(appointments.count()).isZero();
    }

    @Test
    void concurrentIdenticalRequestsCreateOneAppointmentAndOneReservation() throws Exception {
        int callers = 10;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start gate timed out");
                    return post("concurrent-key", 1, 2);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ResponseEntity<JsonNode>> responses = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> future : futures) responses.add(future.get(10, TimeUnit.SECONDS));

            List<Integer> statuses = responses.stream().map(response -> response.getStatusCode().value()).toList();
            assertThat(statuses).hasSize(10).allMatch(status -> status == 200 || status == 201);
            assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(9);
            assertThat(responses).extracting(response -> response.getBody().path("id").asLong()).containsOnly(responses.get(0).getBody().path("id").asLong());
            assertThat(appointments.count()).isEqualTo(1);
            assertThat(appointments.findByIdempotencyKey("concurrent-key")).isPresent();
            assertThat(reservationRequests.get()).isEqualTo(1);
            assertThat(notificationRequests.get()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResponseEntity<JsonNode> post(String key, int patientId, int slotId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        HttpEntity<Map<String, Integer>> request = new HttpEntity<>(Map.of("patientId", patientId, "slotId", slotId), headers);
        return http.exchange("http://localhost:" + port + "/api/appointments", HttpMethod.POST, request, JsonNode.class);
    }

    private ResponseEntity<JsonNode> postWithNotification(String key, int patientId, int slotId) {
        return post(key, patientId, slotId);
    }

    private static void assertError(ResponseEntity<JsonNode> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("message").asText()).isNotBlank();
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }

    private static Path createTemporaryDatabase() {
        try {
            Path directory = Files.createTempDirectory("appointment-http-test-");
            return directory.resolve("appointment-test.db");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
