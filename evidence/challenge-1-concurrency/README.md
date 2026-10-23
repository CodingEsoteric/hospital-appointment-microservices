# Challenge 1: Availability capacity under concurrent requests

## Reproduce

From the repository root, run the Availability Service test suite:

```powershell
mvn -pl services/availability-service test
```

The concurrency verification is `AvailabilityServiceApplicationTests.concurrentReservationsNeverExceedFive`. It starts the service on a random HTTP port and creates a unique SQLite database in JUnit's temporary directory. It creates one slot with capacity 5, releases 20 client threads together, and sends 20 actual HTTP `POST /api/v1/slots/{slotId}/reservations` requests. The service generates reservation UUIDs for successful requests.

The test accepts only HTTP 201 and HTTP 409 responses, checks every 409 body says `Slot capacity exhausted`, checks five distinct reservation IDs, and queries the temporary SQLite database to assert `reserved_count = 5`. A network exception or any other status fails the test; it is not treated as an expected rejection.

`AvailabilityServiceApplicationTests.sqliteBusyTimeoutIsAppliedToPooledConnections` reads `PRAGMA busy_timeout` from the active SQLite JDBC connection and asserts 5000 ms. The production configuration sets this per Hikari connection with `PRAGMA busy_timeout=5000`.

## Recorded result

`availability-maven-test-output.txt` contains the unedited Maven test output from the recorded run, including runtime/SQLite driver startup information, the printed concurrency counts, busy-timeout verification, and Maven's final test summary.

Recorded command (IntelliJ's bundled Maven, offline):

```powershell
$env:JAVA_HOME='C:\Users\HP\.jdks\openjdk-24.0.2'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o -pl services/availability-service test
```

For another machine, use `mvn -pl services/availability-service test` from the repository root with its configured JDK and Maven.

Recorded on 2026-10-23: 6 Availability Service tests passed, with 0 failures, 0 errors, and 0 skipped. The concurrency test reported 20 requests, 5 created reservations, 15 capacity conflicts, 0 other HTTP statuses, 5 distinct reservation IDs, and persisted `reserved_count=5`. SQLite JDBC 3.47.2.0 reported the configured `PRAGMA busy_timeout` as 5000 ms. Maven compiled with Java 21 release settings while the runtime was Java 24.0.2. The test database is temporary and does not use or modify `availability-service.db`.
