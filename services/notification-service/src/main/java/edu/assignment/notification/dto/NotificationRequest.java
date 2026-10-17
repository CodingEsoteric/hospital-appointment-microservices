package edu.assignment.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record NotificationRequest(
        @NotNull @Positive Long appointmentId,
        @NotNull @Positive Long patientId,
        @NotNull @Positive Long slotId,
        @NotBlank String reservationId,
        @NotNull Instant createdAt) {}
