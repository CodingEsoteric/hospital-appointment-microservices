package edu.assignment.appointment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import edu.assignment.appointment.model.Appointment;
import edu.assignment.appointment.repository.AppointmentRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentServiceApplicationTests {
    private static final Path database = createTemporaryDatabase();

    @DynamicPropertySource
    static void temporaryDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + database.toAbsolutePath());
    }

    @Autowired AppointmentRepository appointments;
    @Test
    void contextLoads() {
    }

    @Test
    void databaseEnforcesUniqueIdempotencyKey() {
        appointments.saveAndFlush(new Appointment(1001L, 2001L, "reservation-a", "unique-key", "PENDING"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> appointments.saveAndFlush(
                new Appointment(1002L, 2002L, "reservation-b", "unique-key", "PENDING")));
    }

    @org.junit.jupiter.api.AfterAll
    static void scheduleDatabaseCleanup() {
        database.resolveSibling(database.getFileName() + "-wal").toFile().deleteOnExit();
        database.resolveSibling(database.getFileName() + "-shm").toFile().deleteOnExit();
        database.toFile().deleteOnExit();
        database.getParent().toFile().deleteOnExit();
    }

    private static Path createTemporaryDatabase() {
        try {
            Path directory = Files.createTempDirectory("appointment-context-test-");
            return directory.resolve("appointment-test.db");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
