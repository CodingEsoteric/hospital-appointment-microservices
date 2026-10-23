package edu.assignment.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.assignment.notification.dto.NotificationResponse;
import edu.assignment.notification.repository.NotificationEventRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class NotificationPersistenceRestartTests {
    @TempDir Path tempDir;

    @Test
    void acceptedEventSurvivesApplicationContextRecreation() throws Exception {
        Path database = tempDir.resolve("notification-restart-test.db");
        String databaseUrl = "jdbc:sqlite:" + database.toAbsolutePath();
        String requestBody = "{\"appointmentId\":9901,\"patientId\":77,\"slotId\":88,"
                + "\"reservationId\":\"reservation-9901\",\"createdAt\":\"2026-10-17T12:00:00Z\"}";
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ConfigurableApplicationContext first = null;
        ConfigurableApplicationContext second = null;
        try {
            first = start(databaseUrl);
            HttpResponse<String> initial = post(client, port(first), requestBody);
            assertThat(initial.statusCode()).isEqualTo(202);
            NotificationResponse created = mapper.readValue(initial.body(), NotificationResponse.class);
            assertThat(first.getBean(NotificationEventRepository.class).countByAppointmentId(9901L)).isEqualTo(1);

            first.close();
            first = null;

            second = start(databaseUrl);
            HttpResponse<String> replay = post(client, port(second), requestBody);
            assertThat(replay.statusCode()).isEqualTo(202);
            NotificationResponse restored = mapper.readValue(replay.body(), NotificationResponse.class);
            assertThat(restored.notificationId()).isEqualTo(created.notificationId());
            assertThat(restored.acceptedAt()).isEqualTo(created.acceptedAt());
            assertThat(restored).isEqualTo(created);

            var repository = second.getBean(NotificationEventRepository.class);
            var persisted = repository.findByAppointmentId(9901L).orElseThrow();
            assertThat(repository.countByAppointmentId(9901L)).isEqualTo(1);
            assertThat(persisted.getPatientId()).isEqualTo(77L);
            assertThat(persisted.getSlotId()).isEqualTo(88L);
            assertThat(persisted.getReservationId()).isEqualTo("reservation-9901");
            assertThat(persisted.getCreatedAt()).isEqualTo(Instant.parse("2026-10-17T12:00:00Z"));
            assertThat(persisted.getStatus()).isEqualTo("ACCEPTED");
            System.out.printf("NOTIFICATION_RESTART_RESULT timestamp=%s appointmentId=9901 contexts=2 eventRows=1 sameEventId=true eventDetailsPersisted=true result=PASS%n",
                    Instant.now());
        } finally {
            if (second != null) second.close();
            if (first != null) first.close();
        }
    }

    private static ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(NotificationServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0", "--spring.datasource.url=" + databaseUrl,
                        "--spring.jpa.hibernate.ddl-auto=update", "--spring.main.banner-mode=off");
    }

    private static int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static HttpResponse<String> post(HttpClient client, int port, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notifications"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
