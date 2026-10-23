package edu.assignment.notification.repository;

import edu.assignment.notification.model.NotificationEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, String> {
    Optional<NotificationEvent> findByAppointmentId(Long appointmentId);
    long countByAppointmentId(Long appointmentId);
}
