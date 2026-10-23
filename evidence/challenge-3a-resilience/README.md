# Challenge 3A: Notification resilience evidence

## Focused reproduction

Run from the repository root in PowerShell with JDK 24.0.2 and IntelliJ IDEA 2026.2.2's bundled Maven 3.9.16:

```powershell
$env:JAVA_HOME='C:\Users\HP\.jdks\openjdk-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o -pl services/appointment-service -am '-Dtest=AppointmentHttpIntegrationTests,DownstreamHttpClientFactoryTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`-am` builds the required Availability Service module because Appointment has a test-scope dependency on it. The Surefire filter runs the two named Appointment test classes only. The focused captured run is [appointment-notification-test-output.txt](appointment-notification-test-output.txt): recorded 2026-10-23 23:32:07–23:32:32 +03:00, Java 24.0.2, Maven 3.9.16, exit code 0, 13 tests passed (12 integration tests and 1 timeout configuration test), no failures/errors/skips, `BUILD SUCCESS`.

## Verified Appointment resilience coverage

The tests are in `services/appointment-service/src/test/java/edu/assignment/appointment/AppointmentHttpIntegrationTests.java`; their downstream endpoints are local MockWebServer instances, not a separately running Notification Service.

At runtime, `services/appointment-service/src/main/resources/application.yml` sets connect and response timeouts to 2 s and 3 s by default (`DOWNSTREAM_CONNECT_TIMEOUT`, `DOWNSTREAM_RESPONSE_TIMEOUT`). The notification retry defaults are at most 3 attempts, 100 ms initial delay, 2 s maximum delay, and up to 100 ms jitter, configured by `NOTIFICATION_MAX_ATTEMPTS`, `NOTIFICATION_RETRY_INITIAL_DELAY`, `NOTIFICATION_RETRY_MAX_DELAY`, and `NOTIFICATION_RETRY_JITTER_CAP`. `DownstreamClients` retries I/O/timeouts and HTTP 408, 429, and 5xx; a 400 response is not retried. The focused integration tests override these defaults with short values shown below.

- `successfulBookingReturnsCreatedAndPersistsAppointment`: checks HTTP 201, notification status `SENT`, one attempt/request, one persisted appointment, one reservation, and the notification payload.
- `transientNotificationFailureRetriesThenSucceeds`: returns one HTTP 503 then success; checks `SENT`, two attempts/requests, one appointment, one reservation.
- `permanentNotificationClientErrorIsNotRetried`: returns HTTP 400; checks `FAILED`, one attempt/request, and the persisted appointment.
- `slowNotificationTimesOutAndExhaustsBoundedAttempts`: delays response beyond the configured test timeout; checks HTTP 201 booking response, `FAILED`, exactly three attempts/requests, appointment and reservation persistence. It then replays the same idempotency key and checks HTTP 200 with no additional notification or reservation.
- `configuredNotificationFailureDoesNotFailBooking`: returns repeated HTTP 503; checks booking still returns HTTP 201, notification status `FAILED`, three attempts/requests, one appointment, and one reservation.
- `retryDoesNotSendDuplicateNotification`: replays a successful booking; checks HTTP 200 and that notification and reservation counts remain one.
- `replayReturnsExistingAppointmentWithoutAnotherReservation`: checks the existing booking is returned, only one appointment and reservation exist, and the stored request fingerprint matches the request.

`services/appointment-service/src/test/java/edu/assignment/appointment/service/DownstreamHttpClientFactoryTests.java` contains `jdkHttpClientUsesConfiguredFiniteConnectionTimeout`, which checks that the JDK HTTP client uses the configured 425 ms connection timeout. The integration class sets short test-only connect/response timeouts and retry delays (500 ms, 150 ms, 2 ms initial, 4 ms maximum, 1 ms jitter cap; maximum 3 attempts).

The full Appointment regression run also passed: see [appointment-full-suite-test-output.txt](appointment-full-suite-test-output.txt), recorded 2026-10-23 23:32:42–23:33:10 +03:00, exit code 0. Appointment: 29 passed; required Availability module: 6 passed. This broader capture includes the Availability concurrency test and is separate from the focused Challenge 3A capture.

## Current Notification Service persistence evidence and limits

Appointment's resilience tests verify the Appointment client's behavior against mocks. They do not verify Appointment-to-Notification delivery against a running Notification Service. Current Notification Service persistence tests are in `services/notification-service/src/test/java/edu/assignment/notification/NotificationHttpTests.java` and `NotificationPersistenceRestartTests.java`; their separate captured evidence is in `evidence/notification-persistence/`. That evidence covers local event persistence, duplicate submissions, concurrent requests, and restart persistence. A stored `ACCEPTED` event is not proof of email/SMS delivery or exactly-once external delivery.

Appointment persists the booking before attempting notification and stores the final notification outcome/attempt count. There is no transactional outbox or automatic background reconciliation/retry worker. A failed notification remains recorded as failed; replay returns the saved appointment without retrying notification.
