# Challenge 2: Idempotency evidence

## Reproduce

From the repository root in PowerShell, using the configured JDK and Maven runner:

```powershell
$env:JAVA_HOME='C:\Users\HP\.jdks\openjdk-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o -pl services/availability-service,services/appointment-service -am test
```

The command runs the Availability and Appointment Maven test suites from the reactor. The captured output from the actual run is in [appointment-availability-test-output.txt](appointment-availability-test-output.txt).

## What the evidence exercises

- `AppointmentHttpIntegrationTests.concurrentIdenticalRequestsCreateOneAppointmentAndOneReservation` sends ten simultaneous HTTP requests to one running Appointment Service. They share the same key and JSON body. It asserts one `201`, nine `200` replays, a single appointment ID, one persisted appointment, one availability reservation request, and one notification request.
- `AppointmentHttpIntegrationTests.conflictingKeyReuseReturnsConflictWithoutCreatingAnotherAppointment` reuses a key with a different slot and checks the API's `409` error and unchanged appointment/reservation-request counts.
- `AppointmentMultiInstanceIdempotencyTests.tenSimultaneousRequestsAcrossTwoInstancesUseDatabaseConstraint` starts two independent Appointment Service Spring contexts on random HTTP ports, both configured with one new temporary SQLite appointment database. It uses a barrier in the patient HTTP stub so both instances pass their initial idempotency lookup before proceeding. It also starts the real Availability Service on HTTP with a separate temporary SQLite database. Ten concurrent HTTP requests are split between the two Appointment instances.

The multi-instance test checks all responses are successful (`201` once and `200` nine times) and carry one appointment ID. It reads the isolated databases directly after the requests: there is one appointment row; its reservation ID has one active availability reservation; the losing reservation is marked released; and the slot reserved count is one. The unique `appointments.idempotency_key` index arbitrates concurrent persistence across the two independent service contexts, while compensation releases the losing reservation. These databases are created under JUnit's temporary directory and are not the development databases.

## Actual run result

Run completed 2026-10-23 with Java 24.0.2, Maven offline (`-o`): Availability Service 6 tests passed; Appointment Service 25 tests passed; 0 failures, 0 errors, 0 skipped; Maven `BUILD SUCCESS`. The raw output includes the multi-instance result line and the test summaries. The test uses two independent in-process Spring contexts in one JVM, rather than two separately launched operating-system processes.

The existing service API and response statuses are unchanged. `request_fingerprint` stores a SHA-256 fingerprint of the canonical versioned `patientId` and `slotId` request. Existing rows with a null fingerprint are compared using their persisted fields. No claim is made about notification delivery or cross-host SQLite suitability by this evidence.
