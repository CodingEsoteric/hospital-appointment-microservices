package edu.assignment.notification.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationConstraintInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public NotificationConstraintInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Ensure the business-key index also exists on SQLite databases created by schema update.
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_appointment_id "
                + "ON notification_events (appointment_id)");
    }
}
