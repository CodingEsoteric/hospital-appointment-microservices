# Challenge 3A: Notification resilience evidence

## Reproduce

From the repository root in PowerShell, using the configured JDK and IntelliJ Maven runner:

```powershell
$env:JAVA_HOME='C:\Users\HP\.jdks\openjdk-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o -pl services/notification-service,services/appointment-service -am test
```

The actual captured output is [appointment-notification-test-output.txt](appointment-notification-test-output.txt). Maven includes Availability Service in the reactor because Appointment Service has a test-scope dependency on its application for the Challenge 2 isolated two-instance regression test.

## Implemented settings

Appointment Service uses Spring's `JdkClientHttpRequestFactory` backed by `java.net.http.HttpClient` for patient, availability, and notification calls. Defaults are a 2 second connect timeout and 3 second request/response timeout. They can be set with `DOWNSTREAM_CONNECT_TIMEOUT` and `DOWNSTREAM_RESPONSE_TIMEOUT`. A unit test checks the configured JDK `HttpClient.connectTimeout`, and the slow-header HTTP integration case verifies the configured response timeout interrupts downstream notification calls.

Notification delivery defaults to at most 3 attempts, with 100 ms initial backoff, a 2 second maximum backoff, and up to 100 ms added random jitter. `NOTIFICATION_MAX_ATTEMPTS`, `NOTIFICATION_RETRY_INITIAL_DELAY`, `NOTIFICATION_RETRY_MAX_DELAY`, and `NOTIFICATION_RETRY_JITTER_CAP` configure the policy. Retries apply to I/O errors/timeouts and HTTP 408, 429, or 5xx only. Other HTTP errors such as 400 stop after one attempt. Delays are bounded and use exponential growth with full randomization from zero through the capped delay.

Booking persists the appointment with notification status `PENDING` before attempting notification. It then persists `notificationStatus` and `notificationAttempts`; the response includes those fields. A delivery failure leaves the appointment row persisted. The model has no appointment lifecycle status, so this evidence does not establish a `CONFIRMED` appointment state. Replaying an idempotent booking returns its saved notification status and count without trying notification again or reserving another slot. A separate outbox/recovery worker is not implemented, so a failed delivery remains failed until an operator or future feature addresses it.

## Automated test coverage and result

Appointment's HTTP integration tests use local MockWebServer instances, not external systems or the running Notification Service. They cover first-attempt success, one transient 503 followed by success, persistent 503 exhausting three attempts, a slow response timing out, a permanent 400 not being retried, persistence after failure, and replay of a failed notification without a second send or reservation. Test retry delays and timeouts are overridden to short bounded values to keep runs deterministic and fast. `DownstreamHttpClientFactoryTests` checks that the JDK client receives its configured connection timeout. Existing Challenge 2 HTTP concurrency, changed-payload conflict, idempotent replay, and two-context database race tests run in the same Appointment suite.

Notification Service's own HTTP tests also run, but Appointment-to-Notification was not tested against a separately running Notification Service. The integration assertions use HTTP mocks; they verify the Appointment client's behavior and request bounds without making claims about delivery from Notification Service.

Recorded 2026-10-23 with Java 24.0.2, Maven offline: Availability Service 6 tests passed; Notification Service 4 passed; Appointment Service 29 passed; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`. See the captured output for Surefire summaries and the final Maven result.

### Follow-up Notification Service persistence

Notification Service later gained its own SQLite event store and database-level duplicate protection. The test output above predates that change: its Notification Service tests cover the earlier four-test API suite, while Appointment's resilience tests use HTTP mocks. See the separate Notification persistence evidence for its migration, concurrency, and restart checks; the captured output above is kept as the record of the original Challenge 3A run.
