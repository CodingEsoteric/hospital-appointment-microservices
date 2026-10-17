package edu.assignment.patient.dto;

import edu.assignment.patient.model.Patient;

public record PatientResponse(Long id, String name, String email, boolean active) {
    public static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getName(), patient.getEmail(), patient.isActive());
    }
}
