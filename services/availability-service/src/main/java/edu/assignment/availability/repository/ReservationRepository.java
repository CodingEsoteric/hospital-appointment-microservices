package edu.assignment.availability.repository;
import edu.assignment.availability.model.SlotReservation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ReservationRepository extends JpaRepository<SlotReservation, String> {
    @Modifying
    @Query(value = "UPDATE slot_reservations SET released = 1 WHERE id = :id AND released = 0", nativeQuery = true)
    int markReleasedIfActive(@Param("id") String id);
}
