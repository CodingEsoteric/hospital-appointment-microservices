package edu.assignment.patient.dto;

public record EligibilityResponse(Long patientId, boolean exists, boolean active, boolean eligible) {}
