# Verification scripts and current coverage

There are currently no standalone command-line challenge or end-to-end scripts in this directory. Repeatable HTTP verification is implemented in the Maven test suites:

- Challenge 1: `AvailabilityServiceApplicationTests.concurrentReservationsNeverExceedFive` sends concurrent HTTP requests to Availability Service using an isolated temporary SQLite database.
- Challenge 2: Appointment HTTP integration and multi-instance tests exercise same-key replay, changed-payload conflict, and database race handling. See `evidence/challenge-2-idempotency/README.md` for test names and output.
- Challenge 3A: Appointment HTTP integration tests use local HTTP mocks for notification failures, timeouts, and retries. See `evidence/challenge-3a-resilience/README.md`.
- Notification persistence: Notification HTTP tests cover concurrent duplicate submissions and database persistence across application-context recreation. See `evidence/notification-persistence/README.md`.

Run the complete Maven reactor from the repository root with `mvn test`. Test data uses isolated in-memory or temporary SQLite databases. Captured test output and reproduction notes are under `evidence/`; the captures are historical records and are not updated unless a test is actually rerun.

The assignment may require separately runnable evidence scripts or a running-service end-to-end demo. Check the submission rubric and add those artifacts only after implementing and running them; do not present an automated in-process or mock-based test as a standalone script or external-service test.
