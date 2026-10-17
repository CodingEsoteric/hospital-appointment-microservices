package edu.assignment.availability.repository;
import edu.assignment.availability.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DoctorRepository extends JpaRepository<Doctor, Long> {}
