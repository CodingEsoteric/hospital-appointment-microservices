package edu.assignment.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AvailabilityServiceApplicationTests {
    @TempDir static Path tempDir;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("availability-test.db") + "?busy_timeout=10000");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

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
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20), start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i=0; i<20; i++) results.add(pool.submit(() -> {
            ready.countDown(); start.await();
            try {
                return mvc.perform(post("/api/v1/slots/{id}/reservations", id)).andReturn().getResponse().getStatus();
            } catch (Exception e) { throw new RuntimeException(e); }
        }));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
        int successes = 0, rejected = 0;
        for (Future<Integer> result : results) {
            int status = result.get(30, TimeUnit.SECONDS);
            if (status == 201) successes++; else if (status == 409) rejected++;
        }
        pool.shutdown();
        assertThat(successes).isEqualTo(5);
        assertThat(rejected).isEqualTo(15);
        String json = mvc.perform(get("/api/v1/slots/{id}", id)).andReturn().getResponse().getContentAsString();
        JsonNode slot = mapper.readTree(json);
        assertThat(slot.get("reservedCount").asInt()).isEqualTo(5);
        assertThat(slot.get("remainingCapacity").asInt()).isZero();
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
