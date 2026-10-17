package edu.assignment.appointment.dto;
import edu.assignment.appointment.model.Appointment;
import java.time.Instant;
public record AppointmentResponse(Long id, Long patientId, Long slotId, String reservationId, Instant createdAt, String notificationStatus) {
 public static AppointmentResponse from(Appointment a){return new AppointmentResponse(a.getId(),a.getPatientId(),a.getSlotId(),a.getReservationId(),a.getCreatedAt(),a.getNotificationStatus());}
}
