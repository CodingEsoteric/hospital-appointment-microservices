# Implementation Status and Remaining Work

This document replaces the original starter checklist. The four Spring Boot services and their current tests exist in the repository; it is not a list of missing scaffolding.

## Implemented

- Patient Service on port 8082 owns patient records and exposes create, read, eligibility, and health routes.
- Availability Service on port 8083 owns doctors, slots, and reservations; a conditional database update enforces capacity, and release is idempotent.
- Appointment Service on port 8081 calls Patient, Availability, and Notification over REST; it persists appointments, a unique idempotency key and request fingerprint; it compensates a reservation after persistence failure; notification retries are bounded with finite timeouts.
- Notification Service on port 8084 stores simulated event acceptance in its own SQLite database with a unique appointment key.
- Every service has a health endpoint, Springdoc documentation and service-owned SQLite configuration. Tests use isolated databases; see service READMEs and evidence notes.

## Known limitations / possible follow-up

- Appointment has no retrieve, cancel, or lifecycle/state-transition API or status field. Implementing one requires a separate API/data-model change and lifecycle tests.
- Notification Service simulates acceptance and does not deliver externally. A response lost after acceptance can cause a retry; database deduplication prevents duplicate local event rows but is not exactly-once external delivery.
- REST calls and SQLite files do not participate in a distributed transaction. Crash windows may require reconciliation or a future outbox/workflow design.
- SQLite is suitable for this local assignment, not a multi-host shared production database. Busy timeout and database constraints do not remove that deployment limitation.
- Verify required report diagrams, contribution/reflection sections, AI-use declaration, screenshots/scripts, and demo video against the assignment submission rubric. See [report outline](report-outline.md).

Use [API contracts](api-contracts.md), [data ownership](data-ownership.md), and [business rules](business-rules.md) as the current implementation reference.
