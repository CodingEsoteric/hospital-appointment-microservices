package edu.assignment.appointment.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyConstraintInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public IdempotencyConstraintInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // SQLite's Hibernate schema updater does not apply table unique constraints on update.
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_appointment_idempotency_key " +
                "ON appointments (idempotency_key)");
    }
}
