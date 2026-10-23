package edu.assignment.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.assignment.appointment.repository.AppointmentRepository;
import edu.assignment.appointment.service.BookingService;
import edu.assignment.availability.AvailabilityServiceApplication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.context.WebServerApplicationContext;

/** Exercises the persisted unique-key race across two independent Appointment contexts. */
class AppointmentMultiInstanceIdempotencyTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path tempDir;
    private Path appointmentDatabase;
    private Path availabilityDatabase;
    private MockWebServer patientServer;
    private MockWebServer notificationServer;
    private ConfigurableApplicationContext availabilityContext;
    private ConfigurableApplicationContext appointmentContextOne;
    private ConfigurableApplicationContext appointmentContextTwo;
    private CountDownLatch patientBarrier;
    private AtomicInteger notificationRequests;
    private int slotId;
    private String availabilityBaseUrl;

    @BeforeEach
    void startIsolatedServices() throws Exception {
        appointmentDatabase = tempDir.resolve("appointments-test.db");
        availabilityDatabase = tempDir.resolve("availability-test.db");
        patientBarrier = new CountDownLatch(2);
        notificationRequests = new AtomicInteger();

        patientServer = new MockWebServer();
        patientServer.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                patientBarrier.countDown();
                try {
                    if (!patientBarrier.await(15, TimeUnit.SECONDS)) return json(503, "{}");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return json(503, "{}");
                }
                return json(200, "{\"patientId\":1,\"exists\":true,\"active\":true,\"eligible\":true}");
            }
        });
        patientServer.start();

        notificationServer = new MockWebServer();
        notificationServer.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                notificationRequests.incrementAndGet();
                return json(202, "{\"notificationId\":\"test-notification\",\"status\":\"ACCEPTED\"}");
            }
        });
        notificationServer.start();

        availabilityContext = new SpringApplicationBuilder(AvailabilityServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0", "--spring.datasource.url=" + sqliteUrl(availabilityDatabase),
                        "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.main.banner-mode=off");
        availabilityBaseUrl = baseUrl(availabilityContext);
        slotId = createSlotWithCapacityFive(availabilityBaseUrl);

        String[] arguments = {
                "--server.port=0",
                "--spring.datasource.url=" + sqliteUrl(appointmentDatabase),
                "--spring.jpa.hibernate.ddl-auto=update",
                "--appointment.clients.patient-url=" + patientServer.url("/"),
                "--appointment.clients.availability-url=" + availabilityBaseUrl,
                "--appointment.clients.notification-url=" + notificationServer.url("/"),
                "--appointment.clients.notification-path=/api/notifications"
        };
        appointmentContextOne = new SpringApplicationBuilder(AppointmentServiceApplication.class)
                .web(WebApplicationType.SERVLET).run(arguments);
        appointmentContextTwo = new SpringApplicationBuilder(AppointmentServiceApplication.class)
                .web(WebApplicationType.SERVLET).run(arguments.clone());
    }

    @AfterEach
    void stopIsolatedServices() throws IOException {
        patientBarrier.countDown();
        if (appointmentContextTwo != null) appointmentContextTwo.close();
        if (appointmentContextOne != null) appointmentContextOne.close();
        if (availabilityContext != null) availabilityContext.close();
        if (patientServer != null) patientServer.shutdown();
        if (notificationServer != null) notificationServer.shutdown();
    }

    @Test
    void tenSimultaneousRequestsAcrossTwoInstancesUseDatabaseConstraint() throws Exception {
        assertThat(appointmentContextOne.getBean(BookingService.class))
                .isNotSameAs(appointmentContextTwo.getBean(BookingService.class));
        AppointmentRepository appointments = appointmentContextOne.getBean(AppointmentRepository.class);
        assertThat(appointments.count()).isZero();

        int firstPort = port(appointmentContextOne);
        int secondPort = port(appointmentContextTwo);
        int callers = 10;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        HttpClient client = HttpClient.newHttpClient();
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                int targetPort = i % 2 == 0 ? firstPort : secondPort;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("idempotency start gate timed out");
                    HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + targetPort + "/api/appointments"))
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", "two-instance-same-key")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"patientId\":1,\"slotId\":" + slotId + "}"))
                            .build();
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) responses.add(future.get(30, TimeUnit.SECONDS));
            List<Integer> statuses = responses.stream().map(HttpResponse::statusCode).toList();
            assertThat(statuses).hasSize(callers).allMatch(status -> status == 200 || status == 201);
            assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(9);

            List<Long> appointmentIds = new ArrayList<>();
            for (HttpResponse<String> response : responses) appointmentIds.add(JSON.readTree(response.body()).path("id").asLong());
            assertThat(appointmentIds).hasSize(callers).containsOnly(appointmentIds.get(0));
            assertThat(appointments.count()).isEqualTo(1);
            assertThat(notificationRequests.get()).isEqualTo(1);

            verifyPersistedAppointmentAndReservation(appointmentIds.get(0));
            System.out.printf("CHALLENGE_2_MULTI_INSTANCE_RESULT timestamp=%s requests=%d appInstances=2 created=%d replayed=%d appointmentRows=1 availabilityReservationRows=2 releasedReservations=1 activeSlotReservations=1 slotReservedCount=1 notificationRequests=%d result=PASS%n",
                    Instant.now(), callers, statuses.stream().filter(status -> status == 201).count(),
                    statuses.stream().filter(status -> status == 200).count(), notificationRequests.get());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void verifyPersistedAppointmentAndReservation(long responseAppointmentId) throws Exception {
        String appointmentReservationId;
        long appointmentRows;
        try (var connection = DriverManager.getConnection(sqliteUrl(appointmentDatabase));
             var statement = connection.prepareStatement(
                     "SELECT id, reservation_id FROM appointments WHERE idempotency_key = ?")) {
            statement.setString(1, "two-instance-same-key");
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isEqualTo(responseAppointmentId);
                appointmentReservationId = rows.getString("reservation_id");
                assertThat(rows.next()).isFalse();
            }
            try (var count = connection.createStatement().executeQuery("SELECT COUNT(*) FROM appointments")) {
                assertThat(count.next()).isTrue();
                appointmentRows = count.getLong(1);
            }
        }
        assertThat(appointmentRows).isEqualTo(1);

        try (var connection = DriverManager.getConnection(sqliteUrl(availabilityDatabase));
             PreparedStatement active = connection.prepareStatement(
                     "SELECT id FROM slot_reservations WHERE slot_id = ? AND released = 0")) {
            active.setLong(1, slotId);
            try (ResultSet rows = active.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("id")).isEqualTo(appointmentReservationId);
                assertThat(rows.next()).isFalse();
            }
            try (var count = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM slot_reservations WHERE slot_id = " + slotId + " AND released = 0")) {
                assertThat(count.next()).isTrue();
                assertThat(count.getLong(1)).isEqualTo(1);
            }
            try (var count = connection.createStatement().executeQuery(
                    "SELECT COUNT(*), SUM(CASE WHEN released = 1 THEN 1 ELSE 0 END) FROM slot_reservations WHERE slot_id = " + slotId)) {
                assertThat(count.next()).isTrue();
                assertThat(count.getLong(1)).isEqualTo(2);
                assertThat(count.getLong(2)).isEqualTo(1);
            }
            try (var slot = connection.createStatement().executeQuery(
                    "SELECT reserved_count FROM appointment_slots WHERE id = " + slotId)) {
                assertThat(slot.next()).isTrue();
                assertThat(slot.getInt(1)).isEqualTo(1);
            }
        }
    }

    private int createSlotWithCapacityFive(String baseUrl) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> doctor = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/doctors"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Idempotency Test Doctor\",\"specialty\":\"General\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(doctor.statusCode()).isEqualTo(201);
        long doctorId = JSON.readTree(doctor.body()).path("id").asLong();
        String slotJson = "{\"doctorId\":" + doctorId
                + ",\"startsAt\":\"2030-01-01T10:00:00Z\",\"endsAt\":\"2030-01-01T10:30:00Z\",\"capacity\":5}";
        HttpResponse<String> slot = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/slots"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(slotJson)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(slot.statusCode()).isEqualTo(201);
        return JSON.readTree(slot.body()).path("id").asInt();
    }

    private static String baseUrl(ConfigurableApplicationContext context) {
        return "http://localhost:" + port(context);
    }

    private static int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static String sqliteUrl(Path database) {
        return "jdbc:sqlite:" + database.toAbsolutePath();
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }
}
