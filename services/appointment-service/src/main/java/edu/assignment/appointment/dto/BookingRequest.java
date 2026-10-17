package edu.assignment.appointment.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record BookingRequest(@NotNull @Positive Long patientId, @NotNull @Positive Long slotId) {}
