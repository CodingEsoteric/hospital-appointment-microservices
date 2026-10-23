package edu.assignment.appointment.service;

import java.time.Instant;
import java.time.Duration;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DownstreamClients {
    private final RestClient patient, availability, notification;
    private final String notificationPath;
    private final int notificationMaxAttempts;
    private final Duration retryInitialDelay;
    private final Duration retryMaxDelay;
    private final Duration retryJitterCap;

    public DownstreamClients(String patientUrl, String availabilityUrl, String notificationUrl, String notificationPath) {
        this(patientUrl, availabilityUrl, notificationUrl, notificationPath, Duration.ofSeconds(2),
                Duration.ofSeconds(3), 3, Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(100));
    }

    @Autowired
    public DownstreamClients(@Value("${appointment.clients.patient-url}") String patientUrl,
            @Value("${appointment.clients.availability-url}") String availabilityUrl,
            @Value("${appointment.clients.notification-url}") String notificationUrl,
            @Value("${appointment.clients.notification-path:}") String notificationPath,
            @Value("${appointment.clients.connect-timeout:2s}") Duration connectTimeout,
            @Value("${appointment.clients.response-timeout:3s}") Duration responseTimeout,
            @Value("${appointment.clients.notification-retry.max-attempts:3}") int notificationMaxAttempts,
            @Value("${appointment.clients.notification-retry.initial-delay:100ms}") Duration retryInitialDelay,
            @Value("${appointment.clients.notification-retry.max-delay:2s}") Duration retryMaxDelay,
            @Value("${appointment.clients.notification-retry.jitter-cap:100ms}") Duration retryJitterCap) {
        patient=DownstreamHttpClientFactory.create(patientUrl, connectTimeout, responseTimeout);
        availability=DownstreamHttpClientFactory.create(availabilityUrl, connectTimeout, responseTimeout);
        notification=DownstreamHttpClientFactory.create(notificationUrl, connectTimeout, responseTimeout);
        this.notificationPath=notificationPath;
        this.notificationMaxAttempts=Math.max(1, notificationMaxAttempts);
        this.retryInitialDelay=nonNegative(retryInitialDelay);
        this.retryMaxDelay=nonNegative(retryMaxDelay);
        this.retryJitterCap=nonNegative(retryJitterCap);
    }
    public Eligibility checkPatient(long id) {
        return patient.get().uri("/api/patients/{id}/eligibility", id).retrieve().body(Eligibility.class);
    }
    public Reservation reserve(long slotId) {
        return availability.post().uri("/api/v1/slots/{id}/reservations",slotId).retrieve().body(Reservation.class);
    }
    public void release(String reservationId) {
        availability.post().uri("/api/v1/reservations/{id}/release",reservationId).retrieve().toBodilessEntity();
    }
    public NotificationResult notifyBooking(long appointmentId, long patientId, long slotId, String reservationId, Instant createdAt) {
        if (notificationPath == null || notificationPath.isBlank()) return new NotificationResult(NotificationOutcome.UNCONFIGURED, 0);
        Map<String, Object> payload=Map.of("appointmentId",appointmentId,"patientId",patientId,"slotId",slotId,
                "reservationId",reservationId,"createdAt",createdAt);
        for (int attempt=1; attempt<=notificationMaxAttempts; attempt++) {
            try {
                notification.post().uri(notificationPath).body(payload).retrieve().toBodilessEntity();
                return new NotificationResult(NotificationOutcome.SENT, attempt);
            } catch (ResourceAccessException failure) {
                if (!isTransient(failure) || attempt == notificationMaxAttempts)
                    return new NotificationResult(NotificationOutcome.FAILED, attempt);
            } catch (RestClientResponseException failure) {
                if (!isTransient(failure.getStatusCode()) || attempt == notificationMaxAttempts)
                    return new NotificationResult(NotificationOutcome.FAILED, attempt);
            } catch (RuntimeException failure) {
                return new NotificationResult(NotificationOutcome.FAILED, attempt);
            }
            if (!waitBeforeRetry(attempt)) return new NotificationResult(NotificationOutcome.FAILED, attempt);
        }
        return new NotificationResult(NotificationOutcome.FAILED, notificationMaxAttempts);
    }
    private boolean waitBeforeRetry(int failedAttempt) {
        long base=retryInitialDelay.toMillis();
        for (int i=1; i<failedAttempt && base<retryMaxDelay.toMillis(); i++) base=Math.min(retryMaxDelay.toMillis(), base>Long.MAX_VALUE/2 ? Long.MAX_VALUE : base*2);
        long cap=Math.min(retryMaxDelay.toMillis(), saturatingAdd(base,retryJitterCap.toMillis()));
        long delay=cap <= 0 ? 0 : ThreadLocalRandom.current().nextLong(cap + 1);
        try { if (delay>0) Thread.sleep(delay); return true; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }
    private static boolean isTransient(HttpStatusCode status) {
        return status.value()==408 || status.value()==429 || status.is5xxServerError();
    }
    private static boolean isTransient(ResourceAccessException failure) {
        for (Throwable cause=failure; cause!=null; cause=cause.getCause()) {
            if (cause instanceof UnknownHostException) return false;
            if (cause instanceof IOException) return true;
        }
        return false;
    }
    private static Duration nonNegative(Duration duration) { return duration.isNegative() ? Duration.ZERO : duration; }
    private static long saturatingAdd(long left, long right) { return Long.MAX_VALUE-left<right ? Long.MAX_VALUE : left+right; }
    public record NotificationResult(NotificationOutcome status, int attempts) {}
    public enum NotificationOutcome { UNCONFIGURED, SENT, FAILED }
    public record Eligibility(Long patientId, boolean exists, boolean active, boolean eligible) {}
    public record Reservation(String reservationId, Long slotId, boolean released) {}
}
