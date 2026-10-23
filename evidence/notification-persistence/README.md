# Notification Service persistence evidence

## Reproduce

From the repository root with Java 21 and Maven installed, run:

```powershell
mvn -pl services/notification-service test
mvn -pl services/notification-service,services/appointment-service -am test
```

The second command includes Availability Service in the reactor because Appointment Service's Challenge 2 test suite uses its application in an isolated integration test. The captured full regression run is [notification-appointment-test-output.txt](notification-appointment-test-output.txt). The actual test goal was `mvn -o -pl services/notification-service,services/appointment-service -am test`, run with Java 24.0.2 and IntelliJ's Maven 3.9.16 runner against an isolated temporary copy of the current source tree. `.git`, `.idea`, existing build output, and local database files were excluded. No development database was used. The tested Notification Service source and test files match the current files by SHA-256.

## What the tests verify

- `NotificationHttpTests.acceptsNotificationAndReturnsRecordedEvent` submits a valid HTTP request and checks the existing 202 response.
- `NotificationHttpTests.rejectsInvalidPayloadWithClearBadRequest` checks the existing validation response remains HTTP 400.
- `NotificationHttpTests.duplicateAppointmentReturnsOriginalEvent` verifies replay returns the same response and one stored row.
- `NotificationHttpTests.concurrentDuplicateSubmissionsCreateOnePersistedEvent` releases 20 HTTP clients together with the same appointment payload. It checks every response is 202, each has the same event ID and acceptance timestamp, and the database contains one row for the appointment.
- `NotificationPersistenceRestartTests.acceptedEventSurvivesApplicationContextRecreation` posts to one service context, closes it, starts a second context against the same isolated SQLite file, and verifies a duplicate post returns the same event. It also reads the repository to confirm the event's patient ID, slot ID, reservation ID, creation timestamp, status, and single-row count survived.
- `NotificationHttpTests.healthEndpointRemainsAvailable` verifies `GET /health` remains HTTP 200. The suite also verifies `PRAGMA busy_timeout=5000` on the active SQLite connection.

The concurrency and restart tests use separate temporary databases. Hibernate creates/updates the event table at startup; `NotificationConstraintInitializer` creates the unique index on `appointment_id` automatically. No SQL setup or committed SQLite file is required.

The captured run reports Availability 6 tests passed, Notification 7 tests passed, and Appointment 29 tests passed, with 0 failures, errors, or skips and Maven `BUILD SUCCESS`. The output includes `NOTIFICATION_CONCURRENCY_RESULT` and `NOTIFICATION_RESTART_RESULT` lines with the observed counts.

## Scope and delivery limitation

The stored event is a durable local acceptance record, not proof of email/SMS delivery. Appointment retries can resend an HTTP request after a lost response; this database returns the same event for that appointment ID, but it does not make an external delivery exactly-once.
