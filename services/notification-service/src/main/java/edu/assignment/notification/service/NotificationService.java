package edu.assignment.notification.service;

import edu.assignment.notification.dto.NotificationRequest;
import edu.assignment.notification.dto.NotificationResponse;
import edu.assignment.notification.model.NotificationEvent;
import edu.assignment.notification.repository.NotificationEventRepository;
import org.springframework.stereotype.Service;

/** Persisted local notification simulator; events are not delivered externally. */
@Service
public class NotificationService {
    private final NotificationEventRepository events;

    public NotificationService(NotificationEventRepository events) {
        this.events = events;
    }

    public NotificationResponse accept(NotificationRequest request) {
        var existing = events.findByAppointmentId(request.appointmentId());
        if (existing.isPresent()) return existing.get().toResponse();

        try {
            return events.saveAndFlush(new NotificationEvent(request)).toResponse();
        } catch (RuntimeException insertFailure) {
            // The database unique constraint elects the winner if requests race across threads or instances.
            if (!isAppointmentUniqueConflict(insertFailure)) throw insertFailure;
            return events.findByAppointmentId(request.appointmentId()).orElseThrow(() -> insertFailure).toResponse();
        }
    }

    private static boolean isAppointmentUniqueConflict(Throwable failure) {
        for (Throwable cause=failure; cause!=null; cause=cause.getCause()) {
            String message=cause.getMessage();
            if (message != null && (message.contains("notification_events.appointment_id")
                    || message.contains("uk_notification_appointment_id"))) return true;
        }
        return false;
    }
}
