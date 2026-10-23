# Notification Service

Spring Boot 3.4.5 / Java 21 service on port 8084 by default. It records simulated appointment notification events in its own SQLite database. It does not send email or SMS.

## Run

From the repository root with Java 21 and Maven installed:

```powershell
mvn -pl services/notification-service spring-boot:run
```

The service creates or updates its schema automatically at startup. By default it uses `./notification-service.db` in the process working directory. To use another SQLite database, set `NOTIFICATION_DATABASE_URL` to a SQLite JDBC URL before starting it, for example:

```powershell
$env:NOTIFICATION_DATABASE_URL='jdbc:sqlite:C:/hospital-data/notifications.db'
mvn -pl services/notification-service spring-boot:run
```

The database belongs to Notification Service. It stores the notification event ID, appointment/patient/slot IDs, reservation ID, booking creation time, event status, and acceptance time. The `appointment_id` column has a database uniqueness constraint so concurrent submissions for one appointment return the same persisted event. Local database files and SQLite sidecar files are ignored by Git.

## API

- `GET http://localhost:8084/health` returns `200 OK`.
- `POST http://localhost:8084/api/notifications` accepts JSON with positive numeric `appointmentId`, `patientId`, and `slotId`, a nonblank `reservationId`, and ISO-8601 `createdAt`.

Example:

```json
{
  "appointmentId": 42,
  "patientId": 7,
  "slotId": 9,
  "reservationId": "reservation-42",
  "createdAt": "2026-10-17T12:00:00Z"
}
```

A valid submission returns `202 Accepted` with `notificationId`, `appointmentId`, `status: ACCEPTED`, and `acceptedAt`. Invalid JSON or required fields return `400 Bad Request` with the existing `INVALID_NOTIFICATION` error body. Repeated submissions with the same appointment ID return the original event response, including across service restarts. The first accepted event is retained if a later request reuses that appointment ID with different details.

Swagger UI is available at `/swagger-ui/index.html`; the OpenAPI document is at `/v3/api-docs`.

## Tests

From the repository root:

```powershell
mvn -pl services/notification-service test
mvn -pl services/notification-service,services/appointment-service -am test
```

Tests use isolated temporary SQLite files. They cover HTTP validation, duplicate and concurrent submissions, database persistence after application-context recreation, and health. Appointment Service integration tests use HTTP mocks for notification failures and retries; they do not test delivery against a running Notification Service.

## Delivery limitation

The persisted event means the API acceptance and deduplication survive restart. It is still a local simulation, not a delivery queue or external transport. Appointment Service retries transient failures, but if a notification was accepted and its response was lost, delivery is at-least-once at the HTTP boundary. The appointment ID uniqueness constraint deduplicates submissions within this Notification Service database; it does not guarantee exactly-once email/SMS delivery.
