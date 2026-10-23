# Hospital Appointment Microservices

A Java 21, Spring Boot 3.4.5, Maven application with four independently runnable services. Services communicate through HTTP APIs and own separate SQLite databases. Appointment booking uses Availability Service reservation operations, a database-backed idempotency key, and bounded Notification Service retries. Notifications are simulated records; the system does not deliver email or SMS.

## Services

| Service | Port | Database owner | Main responsibility |
|---|---:|---|---|
| Appointment Service | 8081 | `appointment-service.db` | Booking orchestration, persisted idempotency, notification status |
| Patient Service | 8082 | `patient-service.db` | Patient records and eligibility |
| Availability Service | 8083 | `availability-service.db` | Doctors, slots, capacity-safe reservations and release |
| Notification Service | 8084 | `notification-service.db` | Persisted, deduplicated simulated notification acceptance |

Each database is local to its owning service. Services exchange IDs and data through REST and do not read one another's SQLite files. The SQLite files are created/updated at startup and are ignored by Git.

## Prerequisites and build

- JDK 21 or later (the Maven compiler release is 21)
- Maven 3.9 or later

From the repository root:

```powershell
mvn test
```

Run one service's tests with `mvn -pl services/<service-name> test`. Appointment's integration tests use Availability Service as a test dependency, so Maven also builds that module when needed. Tests use temporary or in-memory databases rather than the default development database files.

## Start the services

Open four terminals in the repository root. Start the services Appointment calls first, then start Appointment Service:

```powershell
mvn -pl services/patient-service spring-boot:run
mvn -pl services/availability-service spring-boot:run
mvn -pl services/notification-service spring-boot:run
mvn -pl services/appointment-service spring-boot:run
```

Each service can also be started independently. Appointment booking requires Patient and Availability Service. Notification Service can be disabled for booking-status purposes by setting `NOTIFICATION_SEND_PATH` to an empty string; this reports `UNCONFIGURED`.

Health checks are `GET /health` on each port. Springdoc provides `/swagger-ui/index.html` and `/v3/api-docs` for each service.

## Configuration

All values and defaults below come from the four `src/main/resources/application.yml` files. `SERVER_PORT` is read by every service. Database URLs are SQLite JDBC URLs; the defaults are relative to the process working directory.

| Variable | Service | Default |
|---|---|---|
| `SERVER_PORT` | all | service port shown above |
| `PATIENT_DATABASE_URL` | Patient | `jdbc:sqlite:./patient-service.db` |
| `AVAILABILITY_DATABASE_URL` | Availability | `jdbc:sqlite:./availability-service.db` |
| `APPOINTMENT_DATABASE_URL` | Appointment | `jdbc:sqlite:./appointment-service.db` |
| `NOTIFICATION_DATABASE_URL` | Notification | `jdbc:sqlite:./notification-service.db` |
| `PATIENT_SERVICE_URL` | Appointment | `http://localhost:8082` |
| `AVAILABILITY_SERVICE_URL` | Appointment | `http://localhost:8083` |
| `NOTIFICATION_SERVICE_URL` | Appointment | `http://localhost:8084` |
| `NOTIFICATION_SEND_PATH` | Appointment | `/api/notifications` |
| `DOWNSTREAM_CONNECT_TIMEOUT` | Appointment | `2s` |
| `DOWNSTREAM_RESPONSE_TIMEOUT` | Appointment | `3s` |
| `NOTIFICATION_MAX_ATTEMPTS` | Appointment | `3` |
| `NOTIFICATION_RETRY_INITIAL_DELAY` | Appointment | `100ms` |
| `NOTIFICATION_RETRY_MAX_DELAY` | Appointment | `2s` |
| `NOTIFICATION_RETRY_JITTER_CAP` | Appointment | `100ms` |

Appointment, Availability, and Notification configure SQLite's `busy_timeout` to 5000 ms. Hibernate `ddl-auto:update` creates/updates service tables. Appointment, Availability, and Notification also create their required unique indexes at startup. Patient Service relies on Hibernate schema initialization. To isolate a local run, set the relevant database variable to a different SQLite path, for example:

```powershell
$env:APPOINTMENT_DATABASE_URL='jdbc:sqlite:C:/hospital-data/appointment-dev.db'
```

See [API contracts](docs/api-contracts.md), [business rules](docs/business-rules.md), and [report outline](docs/report-outline.md). Challenge evidence and reproduction notes are under [`evidence/`](evidence/).

## Current scope and limitations

Appointment Service currently implements booking creation only. There is no appointment retrieval, cancellation endpoint, appointment status field, or cancellation lifecycle/state machine. Notification status (`UNCONFIGURED`, `SENT`, `FAILED`, or persisted `PENDING` when status persistence fails) is separate from appointment lifecycle. Notification events are durable local acceptance records, not external delivery. A lost response can lead to repeated HTTP submission; database deduplication returns the same event but does not provide exactly-once email/SMS delivery. The system does not implement distributed transactions or automatic crash-window reconciliation.
