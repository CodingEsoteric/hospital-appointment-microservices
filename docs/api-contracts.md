# Implemented API Contracts

These routes and payloads reflect the current controllers and DTOs. IDs are numeric JSON values / `Long` path variables unless identified as strings. Responses use JSON. The APIs are not consistently versioned: Availability routes use `/api/v1`, while the other business APIs use `/api`.

Every service exposes `GET /health`, returning HTTP 200 and a JSON object with `status: "UP"` and the service name.

## Patient Service — port 8082

| Method and route | Request | Success response |
|---|---|---|
| `POST /api/patients` | `{ "name": string, "email": string, "active": boolean? }`. `active` defaults to true. Name is nonblank, max 120 characters; email must be valid and max 254 characters. | `201 Created`, `Location: /api/patients/{id}`, body `{ id: Long, name, email, active }` |
| `GET /api/patients/{id}` | Numeric `Long` ID | `200 OK`, `{ id: Long, name, email, active }` |
| `GET /api/patients/{id}/eligibility` | Numeric `Long` ID | `200 OK`, `{ patientId: Long, exists: boolean, active: boolean, eligible: boolean }` |

An unknown patient returns `404` with `{ "error": "Patient not found" }`. Invalid input returns `400` with `{ "error": "Invalid patient input" }`; malformed bodies/path IDs return `400` with `{ "error": "Invalid request" }`. Unexpected errors return `500` with a generic `{ "error": ... }` body. The email column is unique; duplicate-email behavior is not translated to a documented client error by the current handler.

## Availability Service — port 8083

| Method and route | Request | Success response |
|---|---|---|
| `POST /api/v1/doctors` | `{ "name": string, "specialty": string }`, both nonblank | `201 Created`, `Location: /api/v1/doctors/{id}`, body `{ id: Long, name, specialty }` |
| `GET /api/v1/doctors` | None | `200 OK`, array of doctor objects |
| `GET /api/v1/doctors/{id}` | Numeric `Long` doctor ID | `200 OK`, doctor object |
| `POST /api/v1/slots` | `{ "doctorId": Long, "startsAt": Instant, "endsAt": Instant, "capacity": positive integer }` | `201 Created`, `Location: /api/v1/slots/{id}`, slot object |
| `GET /api/v1/slots?doctorId={id}` | Optional numeric `Long` filter | `200 OK`, array of slot objects |
| `GET /api/v1/slots/{id}` | Numeric `Long` slot ID | `200 OK`, slot object |
| `POST /api/v1/slots/{id}/reservations` | No body | `201 Created`, `{ reservationId: string, slotId: Long, released: false }` |
| `POST /api/v1/reservations/{reservationId}/release` | String reservation ID in path | `200 OK`, `{ reservationId: string, slotId: Long, released: true }` |

Slot objects contain `{ id: Long, doctorId: Long, startsAt: Instant, endsAt: Instant, capacity: integer, reservedCount: integer, remainingCapacity: integer }`. `endsAt` must be after `startsAt`. Unknown doctors, slots, and reservations return `404`; exhausted capacity returns `409`. Releasing an already released reservation returns the released view again without decrementing capacity twice. Invalid requests return `400`; service errors return a generic `500`. Error bodies use `{ "error": string }`.

Capacity is enforced in a conditional database update (`reserved_count < capacity`) rather than a read followed by an unconditional write. SQLite busy timeout is configured as 5000 ms.

## Appointment Service — port 8081

| Method and route | Request | Success response |
|---|---|---|
| `POST /api/appointments` | Required `Idempotency-Key` header; body `{ "patientId": positive Long, "slotId": positive Long }` | New booking: `201 Created`; same-key/same-payload replay: `200 OK` |

The response contains `{ id: Long, patientId: Long, slotId: Long, reservationId: string, createdAt: Instant, notificationStatus: string, notificationAttempts: integer }`. There are no appointment GET or cancellation routes in the current controller.

Same key and same payload replays the persisted appointment. Same key with a different payload returns `409`. Missing/blank key and DTO validation failures return `400` with `{ "code": string, "message": string }`; malformed JSON also returns `400` through Spring's default error handling. An existing but inactive/ineligible patient returns `422`; a missing patient is a downstream `404` and remains `404`. Slot conflict maps to `409`, other downstream HTTP errors map to `502`, and downstream connection failures/timeouts map to `503`. Persistence failures and other handled internal errors return `500` with the code/message envelope.

Appointment is persisted before notification delivery. Notification attempts use finite configured connect and response timeouts and a bounded retry policy for transient failures. Notification failures do not remove the persisted appointment. The notification status/attempt count is in the booking response and stored with the appointment; a later failure saving those fields leaves the durable status `PENDING`.

## Notification Service — port 8084

| Method and route | Request | Success response |
|---|---|---|
| `POST /api/notifications` | `{ "appointmentId": positive Long, "patientId": positive Long, "slotId": positive Long, "reservationId": nonblank string, "createdAt": Instant }` | `202 Accepted`, `{ notificationId: string, appointmentId: Long, status: "ACCEPTED", acceptedAt: Instant }` |

Events are stored in Notification Service's own SQLite database. The unique business key is `appointmentId`. Repeated submissions for an appointment return the first persisted event response, even if a later submission has different details. Invalid or malformed payloads return `400` with `{ "code": "INVALID_NOTIFICATION", "message": string }`. Health returns `200`. This service records simulated acceptance; it does not send email or SMS.

## Configuration and docs

Database URLs and downstream endpoint/timeout/retry environment variables, with defaults, are listed in the root [README](../README.md). Each service also serves Swagger UI at `/swagger-ui/index.html` and the OpenAPI document at `/v3/api-docs`.

The services do not currently share a uniform error envelope or propagate a correlation-ID header. Do not assume either behavior in clients.
