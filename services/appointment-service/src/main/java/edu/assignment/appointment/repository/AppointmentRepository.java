package edu.assignment.appointment.repository;
import edu.assignment.appointment.model.Appointment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AppointmentRepository extends JpaRepository<Appointment, Long> { Optional<Appointment> findByIdempotencyKey(String key); }
