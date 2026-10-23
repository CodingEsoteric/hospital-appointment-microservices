package edu.assignment.appointment.service;

import edu.assignment.appointment.dto.BookingRequest;
import edu.assignment.appointment.model.Appointment;
import edu.assignment.appointment.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

@Service
public class BookingService {
    private final AppointmentRepository appointments;
    private final DownstreamClients clients;
    private final Object[] locks = new Object[256];
    public BookingService(AppointmentRepository appointments, DownstreamClients clients) {
        this.appointments=appointments; this.clients=clients; for(int i=0;i<locks.length;i++) locks[i]=new Object();
    }
    public Result book(String key, BookingRequest request) {
        String requestFingerprint = BookingRequestFingerprint.of(request);
        synchronized (locks[(key.hashCode() & 0x7fffffff) % locks.length]) {
            var prior=appointments.findByIdempotencyKey(key);
            if(prior.isPresent()) {
                Appointment a=prior.get();
                if(!samePayload(a, request, requestFingerprint)) throw new KeyReuseException();
                return new Result(a,false);
            }
            DownstreamClients.Eligibility eligibility=clients.checkPatient(request.patientId());
            if(eligibility==null || !eligibility.exists() || !eligibility.active() || !eligibility.eligible()) throw new IneligiblePatientException();
            DownstreamClients.Reservation reservation=clients.reserve(request.slotId());
            if(reservation==null || reservation.reservationId()==null) throw new IllegalStateException("Availability returned an invalid reservation");
            Appointment saved;
            try {
                saved=appointments.saveAndFlush(new Appointment(request.patientId(),request.slotId(),reservation.reservationId(),
                        key,requestFingerprint,"PENDING"));
            } catch (RuntimeException e) {
                compensate(reservation.reservationId(), e);
                if (isIdempotencyConflict(e)) {
                    var raced=appointments.findByIdempotencyKey(key);
                    if (raced.isPresent()) {
                        Appointment existing=raced.get();
                        if (!samePayload(existing, request, requestFingerprint)) throw new KeyReuseException();
                        return new Result(existing,false);
                    }
                }
                throw new PersistenceFailureException(e);
            }
            DownstreamClients.NotificationResult notificationResult=clients.notifyBooking(saved.getId(), saved.getPatientId(), saved.getSlotId(),
                    saved.getReservationId(), saved.getCreatedAt());
            saved.setNotificationStatus(notificationResult.status().name());
            saved.setNotificationAttempts(notificationResult.attempts());
            try { appointments.saveAndFlush(saved); } catch (RuntimeException ignored) {
                // The first save committed the appointment; retain PENDING as its durable retry state.
                saved.setNotificationStatus("PENDING");
            }
            return new Result(saved,true);
        }
    }
    private static boolean samePayload(Appointment a, BookingRequest request, String requestFingerprint) {
        if (a.getRequestFingerprint() != null) return a.getRequestFingerprint().equals(requestFingerprint);
        // Rows created before request_fingerprint was added remain replayable from their persisted request fields.
        return a.getPatientId().equals(request.patientId()) && a.getSlotId().equals(request.slotId());
    }
    private static boolean isIdempotencyConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && (message.contains("appointments.idempotency_key")
                    || message.contains("uk_appointment_idempotency_key"))) return true;
        }
        return false;
    }
    private void compensate(String reservationId, RuntimeException persistenceFailure) {
        try { clients.release(reservationId); } catch(RuntimeException compensationFailure) { persistenceFailure.addSuppressed(compensationFailure); }
    }
    public record Result(Appointment appointment, boolean created) {}
    public static class KeyReuseException extends RuntimeException {}
    public static class IneligiblePatientException extends RuntimeException {}
    public static class PersistenceFailureException extends RuntimeException { public PersistenceFailureException(Throwable cause){super(cause);} }
}
