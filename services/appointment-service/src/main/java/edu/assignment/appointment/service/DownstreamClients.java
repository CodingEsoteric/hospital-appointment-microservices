package edu.assignment.appointment.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DownstreamClients {
    private final RestClient patient, availability, notification;
    private final String notificationPath;
    public DownstreamClients(@Value("${appointment.clients.patient-url}") String patientUrl,
            @Value("${appointment.clients.availability-url}") String availabilityUrl,
            @Value("${appointment.clients.notification-url}") String notificationUrl,
            @Value("${appointment.clients.notification-path:}") String notificationPath) {
        patient=RestClient.create(patientUrl); availability=RestClient.create(availabilityUrl);
        notification=RestClient.create(notificationUrl); this.notificationPath=notificationPath;
    }
    public Eligibility checkPatient(long id) {
        return patient.get().uri("/api/patients/{id}/eligibility", id).retrieve().body(Eligibility.class);
    }
    public Reservation reserve(long slotId) {
        return availability.post().uri("/api/v1/slots/{id}/reservations",slotId).retrieve().body(Reservation.class);
    }
    public void release(String reservationId) {
        availability.post().uri("/api/v1/reservations/{id}/release",reservationId).retrieve().toBodilessEntity();
    }
    public NotificationOutcome notifyBooking(long appointmentId, long patientId, long slotId) {
        if (notificationPath == null || notificationPath.isBlank()) return NotificationOutcome.UNCONFIGURED;
        try {
            notification.post().uri(notificationPath).body(Map.of("appointmentId",appointmentId,"patientId",patientId,"slotId",slotId)).retrieve().toBodilessEntity();
            return NotificationOutcome.SENT;
        } catch (RuntimeException ignored) { return NotificationOutcome.FAILED; }
    }
    public enum NotificationOutcome { UNCONFIGURED, SENT, FAILED }
    public record Eligibility(Long patientId, boolean exists, boolean active, boolean eligible) {}
    public record Reservation(String reservationId, Long slotId, boolean released) {}
}
