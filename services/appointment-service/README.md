# Appointment Service

Spring Boot 3.4.5 / Java 21 service, default port 8081. It owns `appointment-service.db` and coordinates patient eligibility, slot reservation, persisted idempotency, and simulated notification acceptance over REST. It does not access other service databases.

## Start

Start Patient, Availability, and (if notification status should be sent) Notification Service first. From the repository root:

```powershell
mvn -pl services/appointment-service spring-boot:run
```

`GET /health` returns the service health JSON. Swagger UI and OpenAPI are at `/swagger-ui/index.html` and `/v3/api-docs`.

## Booking API

`POST /api/appointments` requires `Idempotency-Key` and JSON `{ "patientId": 1, "slotId": 2 }`, with positive numeric IDs. A new booking returns `201 Created`; a replay with the same key and body returns `200 OK` and the same appointment. Reusing a key with another body returns `409 Conflict`.

The response fields are `id` (numeric), `patientId`, `slotId`, `reservationId`, `createdAt`, `notificationStatus`, and `notificationAttempts`. Missing/invalid header or DTO body returns `400`; malformed JSON also returns `400` through Spring's default error handling. An existing but inactive/ineligible patient returns `422`, while a missing patient returns the downstream `404`. An unavailable slot returns `409`; other downstream HTTP errors return `502`, downstream connection/timeout errors return `503`, and persistence/internal errors return `500`. Handled errors use `{ code, message }` JSON. There are no appointment retrieval or cancellation routes.

The booking flow checks patient eligibility, reserves capacity, saves an appointment with a unique idempotency key and canonical SHA-256 request fingerprint, then attempts notification delivery. Same-key race winners are reloaded from the database; the losing reservation is released. If persistence fails after reservation, a release is attempted. Notification failure does not remove the saved appointment. If the notification-status update itself cannot be saved, the returned status is `PENDING` and the earlier appointment row remains.

Notification attempts are bounded (default 3) and retry transient failures with exponential backoff and jitter. Defaults: connect timeout 2 seconds, response timeout 3 seconds, initial delay 100 ms, maximum delay 2 seconds, jitter cap 100 ms. A blank `NOTIFICATION_SEND_PATH` disables notification calls and records `UNCONFIGURED` with zero attempts. The normal path is `/api/notifications`.

## Configuration and database

| Environment variable | Default |
|---|---|
| `SERVER_PORT` | `8081` |
| `APPOINTMENT_DATABASE_URL` | `jdbc:sqlite:./appointment-service.db` |
| `PATIENT_SERVICE_URL` | `http://localhost:8082` |
| `AVAILABILITY_SERVICE_URL` | `http://localhost:8083` |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8084` |
| `NOTIFICATION_SEND_PATH` | `/api/notifications` |
| `DOWNSTREAM_CONNECT_TIMEOUT` | `2s` |
| `DOWNSTREAM_RESPONSE_TIMEOUT` | `3s` |
| `NOTIFICATION_MAX_ATTEMPTS` | `3` |
| `NOTIFICATION_RETRY_INITIAL_DELAY` | `100ms` |
| `NOTIFICATION_RETRY_MAX_DELAY` | `2s` |
| `NOTIFICATION_RETRY_JITTER_CAP` | `100ms` |

Hibernate creates/updates tables at startup, and an application initializer creates the unique index on `idempotency_key`. SQLite `busy_timeout` is 5000 ms. The database path is relative to the process working directory; set `APPOINTMENT_DATABASE_URL` for an isolated local database. Tests use temporary SQLite databases and mock downstream HTTP services. Challenge 2 also starts two Appointment Service contexts against one isolated database.

## Tests

```powershell
mvn -pl services/appointment-service test
mvn -pl services/appointment-service -am test
```

The tests cover booking, replay/conflict, database race recovery, capacity reservation integration, timeout/retry behavior, and graceful notification failure. They do not implement an appointment cancellation lifecycle. See the root API contracts and challenge evidence for exact limits and test output.
