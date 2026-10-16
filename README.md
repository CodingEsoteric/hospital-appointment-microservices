# Hospital Appointment System

A Java 21 / Spring Boot microservices assignment demonstrating REST communication, database-per-service, concurrency control, idempotency, and resilience.

> This repository is a starter scaffold. It contains the project specification and structure, not a completed or tested implementation. Do not claim tests or challenges pass until you have implemented and run them.

## Architecture
Four independently runnable services in one GitHub repository:

| Service | Default port | Own database | Responsibility |
|---|---:|---|---|
| Appointment Service | 8081 | `appointments.db` | Booking orchestration, appointment state, idempotency |
| Patient Service | 8082 | `patients.db` | Patient records and active status |
| Doctor & Availability Service | 8083 | `availability.db` | Doctors, slots, atomic reservation/release |
| Notification Service | 8084 | `notifications.db` | Mock delivery attempts and simulated failures |

Services communicate only through REST APIs. A service must never read another service's database. Each service will have its own `pom.xml`, Spring Boot entry point, configuration, tests, `/health` endpoint and OpenAPI documentation.

## Suggested setup
- Java 21
- Spring Boot 3.x (choose one compatible version and use it consistently)
- Maven
- Spring Web, Validation, Spring Data JPA, Spring Boot Test
- SQLite with a compatible JDBC driver and Hibernate dialect; verify compatibility with the selected Spring Boot/Hibernate versions
- `RestClient` for service-to-service REST calls
- Springdoc OpenAPI for Swagger UI

SQLite is selected to minimise setup. Keep a separate SQLite file and credentials/configuration per service. Do not configure services to share a database file.

## Repository structure
```text
hospital-appointment-system/
├── README.md
├── .gitignore
├── pom.xml
├── docs/
├── services/
│   ├── appointment-service/
│   ├── patient-service/
│   ├── availability-service/
│   └── notification-service/
├── scripts/
└── evidence/
```

## Business flow
1. Client sends `POST /api/v1/appointments` with an `Idempotency-Key`.
2. Appointment Service claims the key and stores a fingerprint of the request.
3. It validates the patient through Patient Service.
4. It asks Availability Service to atomically reserve the slot.
5. It persists the appointment and the final response for safe replay.
6. It calls Notification Service using a finite timeout and bounded retries.
7. Notification failure must be visible but must not duplicate the booking or silently undo a confirmed appointment.

Document and test the compensation/release path if slot reservation succeeds but appointment persistence fails. This is not a distributed ACID transaction; explain residual crash-window limitations honestly.

## Business rules
- BR1: Only a known, active patient can book.
- BR2: Slot reservation never exceeds slot capacity.
- BR3: Same idempotency key and same payload returns the original result with one effect, including concurrent repeats.
- BR4: Same key with a different payload returns `409 Conflict` (or documented `422`).
- BR5: Notification failure does not by itself cancel a confirmed appointment.
- BR6: Appointment state transitions follow the documented state machine.

Map each implemented rule to code and at least one test.

## Required challenge evidence
- Challenge 1 (mandatory): at least 20 parallel requests against a capacity-limited slot; exactly N succeed for capacity N. Include script, raw output, final database state and explanation of why check-then-update fails.
- Challenge 2 (mandatory): same-key sequential and simultaneous requests produce one effect; changed payload with same key is rejected. Include logs/output and database proof.
- Challenge 3A (selected): notification timeout/failure, bounded retry with exponential backoff and jitter, graceful failure and proof that retry did not create duplicate appointments.

## Run status
The service folders are scaffolds. Generate/implement each Spring Boot service before trying to run it. See `docs/implementation-plan.md`.

## Submission checklist
- [ ] Four independent services, each with its own database and `GET /health`.
- [ ] Versioned APIs, validation, consistent JSON error body, OpenAPI docs.
- [ ] Environment-based configuration, structured logs and propagated `X-Correlation-ID`.
- [ ] Four or more business rules mapped to tests.
- [ ] End-to-end booking script.
- [ ] Concurrency, idempotency and resilience scripts with real output.
- [ ] Evidence folder contains scripts, raw output, timestamped screenshots and explanation.
- [ ] Report PDF within 15 pages excluding appendices.
- [ ] Demo video 8–10 minutes; every member speaks.
- [ ] Contribution table, individual reflections and AI-use declaration.
- [ ] Fresh clone tested by following only this README.
