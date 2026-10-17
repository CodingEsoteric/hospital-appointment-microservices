package edu.assignment.availability.repository;

import edu.assignment.availability.model.AppointmentSlot;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<AppointmentSlot, Long> {
    List<AppointmentSlot> findAllByDoctorId(Long doctorId);
    @Modifying
    @Query(value = "UPDATE appointment_slots SET reserved_count = reserved_count + 1 WHERE id = :id AND reserved_count < capacity", nativeQuery = true)
    int reserveIfAvailable(@Param("id") Long id);
    @Modifying
    @Query(value = "UPDATE appointment_slots SET reserved_count = reserved_count - 1 WHERE id = :id AND reserved_count > 0", nativeQuery = true)
    int decrementIfPositive(@Param("id") Long id);
}
