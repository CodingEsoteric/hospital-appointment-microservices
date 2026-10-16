# API Contracts — Version 1

Use JSON, versioned routes and one error structure across all services. Every service has `GET /health` and OpenAPI/Swagger documentation.

## Error response
```json
{
  "error": {
    "code": "SLOT_UNAVAILABLE",
    "message": "The selected appointment slot is no longer available.",
    "correlationId": "req-123"
  }
}
```

## Patient Service — 8082
- `GET /api/v1/patients/{patientId}` — return patient ID, name and active status.
- `GET /health`

## Doctor & Availability Service — 8083
- `GET /api/v1/doctors` — list seeded doctors.
- `GET /api/v1/slots/{slotId}` — inspect slot state.
- `POST /api/v1/slots/{slotId}/reservations` — atomically reserve a slot.
- `DELETE /api/v1/reservations/{reservationId}` — release reservation for compensation.
- `GET /health`

## Appointment Service — 8081
- `POST /api/v1/appointments` — create appointment; requires `Idempotency-Key`.
- `GET /api/v1/appointments/{appointmentId}` — retrieve appointment.
- `POST /api/v1/appointments/{appointmentId}/cancel` — cancel only from allowed state.
- `GET /health`

Booking request:
```json
{
  "patientId": "P1001",
  "slotId": "SLOT-001"
}
```
Successful booking should return an appointment ID, appointment status and notification status. Same key/same payload replays the stored result; same key/different payload returns 409.

## Notification Service — 8084
- `POST /api/v1/notifications` — record/attempt a mock notification.
- `GET /api/v1/notifications/{notificationId}` — inspect status.
- `GET /health`

For resilience tests, configure a delay/failure through environment variables or a test-only endpoint. Do not expose fault-injection controls in a real production deployment.

## Headers
- `X-Correlation-ID`: create if absent and propagate unchanged to downstream calls/logs.
- `Idempotency-Key`: required for appointment creation.
- Every downstream HTTP call must use a finite timeout.
