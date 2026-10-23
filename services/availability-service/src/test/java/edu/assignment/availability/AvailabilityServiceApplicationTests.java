package edu.assignment.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AvailabilityServiceApplicationTests {
    @TempDir static Path tempDir;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("availability-test.db"));
    }
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private long doctor() throws Exception {
        String json = mvc.perform(post("/api/v1/doctors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Dr Test\",\"specialty\":\"General\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }
    private long slot(long doctorId, int capacity) throws Exception {
        String body = "{\"doctorId\":" + doctorId + ",\"startsAt\":\"2030-01-01T10:00:00Z\",\"endsAt\":\"2030-01-01T10:30:00Z\",\"capacity\":" + capacity + "}";
        String json = mvc.perform(post("/api/v1/slots").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }

    @Test void createsAndValidatesSlot() throws Exception {
        long doctorId = doctor();
        mvc.perform(post("/api/v1/slots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":" + doctorId + ",\"startsAt\":\"2030-01-01T10:00:00Z\",\"endsAt\":\"2030-01-01T09:00:00Z\",\"capacity\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        long slotId = slot(doctorId, 2);
        mvc.perform(get("/api/v1/slots/{id}", slotId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCapacity").value(2));
        mvc.perform(post("/api/v1/slots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":" + doctorId + ",\"startsAt\":\"2030-01-01T10:00:00Z\",\"endsAt\":\"2030-01-01T11:00:00Z\",\"capacity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test void reservesUntilCapacityThenRejects() throws Exception {
        long id = slot(doctor(), 1);
        mvc.perform(post("/api/v1/slots/{id}/reservations", id)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/slots/{id}/reservations", id)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/slots/{id}", id)).andExpect(jsonPath("$.remainingCapacity").value(0));
    }

    @Test void concurrentReservationsNeverExceedFive() throws Exception {
        long id = slot(doctor(), 5);
        HttpClient client = HttpClient.newHttpClient();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20), start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> results = new ArrayList<>();
        try {
            for (int i=0; i<20; i++) results.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency start gate timed out");
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                                + "/api/v1/slots/" + id + "/reservations"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> result : results) responses.add(result.get(30, TimeUnit.SECONDS));

            List<Integer> statuses = responses.stream().map(HttpResponse::statusCode).toList();
            assertThat(statuses).hasSize(20).allMatch(status -> status == 201 || status == 409);
            assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(5);
            assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(15);
            List<HttpResponse<String>> conflicts = responses.stream().filter(response -> response.statusCode() == 409).toList();
            for (HttpResponse<String> conflict : conflicts) {
                assertThat(mapper.readTree(conflict.body()).path("error").asText()).isEqualTo("Slot capacity exhausted");
            }
            long otherHttpStatuses = statuses.stream().filter(status -> status != 201 && status != 409).count();
            assertThat(otherHttpStatuses).isZero();

            List<String> reservationIds = responses.stream().filter(response -> response.statusCode() == 201)
                    .map(response -> {
                        try { return mapper.readTree(response.body()).path("reservationId").asText(); }
                        catch (Exception e) { throw new AssertionError("Successful response was not valid JSON", e); }
                    }).toList();
            assertThat(reservationIds).hasSize(5).doesNotHaveDuplicates();
            assertThat(reservationIds).allSatisfy(reservationId -> assertThat(reservationId).isNotBlank());

            Integer persistedReservedCount = jdbc.queryForObject(
                    "SELECT reserved_count FROM appointment_slots WHERE id = ?", Integer.class, id);
            assertThat(persistedReservedCount).isEqualTo(5);
            System.out.printf("CHALLENGE_1_RESULT timestamp=%s slotId=%d capacity=5 requests=%d created=%d unavailableConflicts=%d otherHttpStatuses=%d distinctReservationIds=%d persistedReservedCount=%d result=PASS%n",
                    java.time.Instant.now(), id, statuses.size(), statuses.stream().filter(status -> status == 201).count(),
                    conflicts.size(), otherHttpStatuses, reservationIds.size(), persistedReservedCount);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void sqliteBusyTimeoutIsAppliedToPooledConnections() {
        Integer busyTimeoutMillis = jdbc.queryForObject("PRAGMA busy_timeout", Integer.class);
        assertThat(busyTimeoutMillis).isEqualTo(5000);
        String driverVersion = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDriverVersion());
        System.out.printf("SQLITE_BUSY_TIMEOUT_RESULT driver=%s pragmaBusyTimeoutMillis=%d result=PASS%n", driverVersion, busyTimeoutMillis);
    }

    @Test void duplicateReleaseDoesNotDecrementTwice() throws Exception {
        long id = slot(doctor(), 2);
        String json = mvc.perform(post("/api/v1/slots/{id}/reservations", id)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reservation = mapper.readTree(json).get("reservationId").asText();
        mvc.perform(post("/api/v1/reservations/{reservationId}/release", reservation)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/reservations/{reservationId}/release", reservation)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/slots/{id}", id)).andExpect(jsonPath("$.reservedCount").value(0))
                .andExpect(jsonPath("$.remainingCapacity").value(2));
    }

    @Test void healthIsAvailable() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
