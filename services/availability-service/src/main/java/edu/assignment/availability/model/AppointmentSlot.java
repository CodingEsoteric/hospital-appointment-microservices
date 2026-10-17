package edu.assignment.availability.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "appointment_slots")
public class AppointmentSlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "doctor_id", nullable = false) private Doctor doctor;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(nullable = false) private int capacity;
    @Column(name = "reserved_count", nullable = false) private int reservedCount = 0;
    protected AppointmentSlot() {}
    public AppointmentSlot(Doctor doctor, Instant startsAt, Instant endsAt, int capacity) {
        this.doctor = doctor; this.startsAt = startsAt; this.endsAt = endsAt; this.capacity = capacity;
    }
    public Long getId() { return id; }
    public Doctor getDoctor() { return doctor; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getCapacity() { return capacity; }
    public int getReservedCount() { return reservedCount; }
}
