package edu.assignment.notification.service;

import edu.assignment.notification.dto.NotificationRequest;
import edu.assignment.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** In-memory notification simulator; events are accepted locally and are not delivered externally. */
@Service
public class NotificationService {
    private final ConcurrentHashMap<Long, NotificationResponse> eventsByAppointment = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public NotificationResponse accept(NotificationRequest request) {
        return eventsByAppointment.computeIfAbsent(request.appointmentId(), id ->
                new NotificationResponse("notification-" + sequence.incrementAndGet(), id, "ACCEPTED", Instant.now()));
    }
}
