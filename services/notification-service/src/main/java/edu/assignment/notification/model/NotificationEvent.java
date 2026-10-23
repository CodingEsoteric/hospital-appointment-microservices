package edu.assignment.notification.model;

import edu.assignment.notification.dto.NotificationRequest;
import edu.assignment.notification.dto.NotificationResponse;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "notification_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_appointment_id", columnNames = "appointment_id"))
public class NotificationEvent implements Persistable<String> {
    @Id
    @Column(name = "notification_id", nullable = false, updatable = false)
    private String notificationId;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private Long appointmentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private Long patientId;

    @Column(name = "slot_id", nullable = false, updatable = false)
    private Long slotId;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private String reservationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "event_status", nullable = false)
    private String status;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Transient
    private boolean newEvent = true;

    protected NotificationEvent() {}

    public NotificationEvent(NotificationRequest request) {
        this.notificationId = "notification-" + UUID.randomUUID();
        this.appointmentId = request.appointmentId();
        this.patientId = request.patientId();
        this.slotId = request.slotId();
        this.reservationId = request.reservationId();
        this.createdAt = request.createdAt();
        this.status = "ACCEPTED";
        this.acceptedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Override
    public String getId() { return notificationId; }

    @Override
    @Transient
    public boolean isNew() { return newEvent; }

    @PostLoad
    @PostPersist
    private void markPersisted() { newEvent = false; }

    public NotificationResponse toResponse() {
        return new NotificationResponse(notificationId, appointmentId, status, acceptedAt);
    }

    public String getNotificationId() { return notificationId; }
    public Long getAppointmentId() { return appointmentId; }
    public Long getPatientId() { return patientId; }
    public Long getSlotId() { return slotId; }
    public String getReservationId() { return reservationId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public Instant getAcceptedAt() { return acceptedAt; }
}
