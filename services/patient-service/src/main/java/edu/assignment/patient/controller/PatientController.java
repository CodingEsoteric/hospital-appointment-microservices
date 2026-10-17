package edu.assignment.patient.controller;

import edu.assignment.patient.dto.CreatePatientRequest;
import edu.assignment.patient.dto.EligibilityResponse;
import edu.assignment.patient.dto.PatientResponse;
import edu.assignment.patient.model.Patient;
import edu.assignment.patient.repository.PatientRepository;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientRepository patients;

    public PatientController(PatientRepository patients) { this.patients = patients; }

    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        Patient patient = patients.save(new Patient(request.name().trim(), request.email().trim(),
                request.active() == null || request.active()));
        return ResponseEntity.created(URI.create("/api/patients/" + patient.getId()))
                .body(PatientResponse.from(patient));
    }

    @GetMapping("/{id}")
    public PatientResponse get(@PathVariable("id") Long id) {
        return PatientResponse.from(findPatient(id));
    }

    @GetMapping("/{id}/eligibility")
    public EligibilityResponse eligibility(@PathVariable("id") Long id) {
        Patient patient = findPatient(id);
        return new EligibilityResponse(id, true, patient.isActive(), patient.isActive());
    }

    private Patient findPatient(Long id) {
        return patients.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Patient not found"));
    }
}
