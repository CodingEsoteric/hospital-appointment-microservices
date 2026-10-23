package edu.assignment.appointment.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "appointments", indexes = @Index(name = "uk_appointment_idempotency_key", columnList = "idempotency_key", unique = true))
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "patient_id", nullable = false) private Long patientId;
    @Column(name = "slot_id", nullable = false) private Long slotId;
    @Column(name = "reservation_id", nullable = false) private String reservationId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "request_fingerprint", length = 64) private String requestFingerprint;
    @Column(name = "notification_status", nullable = false) private String notificationStatus;
    @Column(name = "notification_attempts") private Integer notificationAttempts;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Appointment() {}
    public Appointment(Long patientId, Long slotId, String reservationId, String idempotencyKey, String notificationStatus) {
        this(patientId, slotId, reservationId, idempotencyKey, null, notificationStatus);
    }
    public Appointment(Long patientId, Long slotId, String reservationId, String idempotencyKey,
            String requestFingerprint, String notificationStatus) {
        this.patientId=patientId; this.slotId=slotId; this.reservationId=reservationId;
        this.idempotencyKey=idempotencyKey; this.requestFingerprint=requestFingerprint;
        this.notificationStatus=notificationStatus; this.notificationAttempts=0; this.createdAt=Instant.now();
    }
    public Long getId(){return id;} public Long getPatientId(){return patientId;} public Long getSlotId(){return slotId;}
    public String getReservationId(){return reservationId;} public String getIdempotencyKey(){return idempotencyKey;}
    public String getRequestFingerprint(){return requestFingerprint;}
    public String getNotificationStatus(){return notificationStatus;} public Instant getCreatedAt(){return createdAt;}
    public int getNotificationAttempts(){return notificationAttempts == null ? 0 : notificationAttempts;}
    public void setNotificationStatus(String status){notificationStatus=status;}
    public void setNotificationAttempts(int attempts){notificationAttempts=attempts;}
}
