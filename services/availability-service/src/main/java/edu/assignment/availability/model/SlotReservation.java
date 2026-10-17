package edu.assignment.availability.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "slot_reservations")
public class SlotReservation {
    @Id private String id;
    @Column(name = "slot_id", nullable = false) private Long slotId;
    @Column(nullable = false) private boolean released = false;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected SlotReservation() {}
    public SlotReservation(Long slotId) { this.id = UUID.randomUUID().toString(); this.slotId = slotId; this.createdAt = Instant.now(); }
    public String getId() { return id; }
    public Long getSlotId() { return slotId; }
    public boolean isReleased() { return released; }
    public void release() { this.released = true; }
}
