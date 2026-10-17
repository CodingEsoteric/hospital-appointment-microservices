package edu.assignment.notification.dto;

import java.time.Instant;

public record NotificationResponse(String notificationId, Long appointmentId, String status, Instant acceptedAt) {}
