# Availability Service

Spring Boot 3.4.5 / Java 21 service, default port 8083. It owns `availability-service.db`, including doctors, appointment slots, and reservations. Other services use its REST API; they do not read this database directly.

## Start and docs

From the repository root:

```powershell
mvn -pl services/availability-service spring-boot:run
```

`GET /health` returns service health. Swagger UI and OpenAPI are at `/swagger-ui/index.html` and `/v3/api-docs`.

## Implemented API

- `POST /api/v1/doctors`: create a doctor with nonblank `name` and `specialty`; returns `201` and a `Location` header.
- `GET /api/v1/doctors` and `GET /api/v1/doctors/{id}`: list/read doctors. IDs are numeric.
- `POST /api/v1/slots`: create a slot with numeric `doctorId`, ISO timestamps `startsAt`/`endsAt`, and positive integer `capacity`; end must be after start. Returns `201` and a `Location` header.
- `GET /api/v1/slots` optionally accepts numeric `doctorId`; `GET /api/v1/slots/{id}` reads a slot. Slot response includes capacity, reserved count, and remaining capacity.
- `POST /api/v1/slots/{id}/reservations`: atomically reserve one capacity unit; returns `201` with a string reservation ID. Exhausted capacity returns `409`, missing slot `404`.
- `POST /api/v1/reservations/{reservationId}/release`: idempotently releases an active reservation and returns its state; missing reservation returns `404`.

Validation and malformed requests return `400`; error responses use `{ "error": string }`. The API methods and exact DTO fields are listed in the root [API contracts](../../docs/api-contracts.md).

## Capacity and database

Capacity is enforced by a single conditional SQLite update (`reserved_count = reserved_count + 1` only when `reserved_count < capacity`), not by a separate read and write. The reservation is saved in the same service transaction. Release marks a reservation active-to-released once and decrements only while the count is positive. SQLite `busy_timeout` is set to 5000 ms and verified by a test.

| Environment variable | Default |
|---|---|
| `SERVER_PORT` | `8083` |
| `AVAILABILITY_DATABASE_URL` | `jdbc:sqlite:./availability-service.db` |

Hibernate creates/updates tables at startup. Tests use temporary SQLite files and create distinct doctors/slots for each test run; they do not use the default development database.

## Tests

```powershell
mvn -pl services/availability-service test
```

`AvailabilityServiceApplicationTests.concurrentReservationsNeverExceedFive` sends 20 simultaneous HTTP reservation requests to a capacity-5 slot and verifies five `201` responses, fifteen capacity `409` responses, and the final persisted count. Additional tests cover reservation release, validation, and capacity rejection. Captured output and reproduction notes are in [`evidence/challenge-1-concurrency`](../../evidence/challenge-1-concurrency/).
