# Patient Service

Spring Boot 3.4.5 / Java 21 service, default port 8082. It owns the patient records and SQLite database. Appointment Service reads eligibility through this service's REST API; it does not access the Patient database.

## Start

From the repository root:

```powershell
mvn -pl services/patient-service spring-boot:run
```

`GET /health` returns service health. Swagger UI and OpenAPI are at `/swagger-ui/index.html` and `/v3/api-docs`.

## Implemented API

- `POST /api/patients` creates a patient from `{ "name": string, "email": string, "active": boolean? }`. Name must be nonblank and at most 120 characters; email must be valid and at most 254 characters. `active` defaults to true. Returns `201 Created`, a `Location` header, and `{ id: Long, name, email, active }`.
- `GET /api/patients/{id}` reads a patient by numeric ID.
- `GET /api/patients/{id}/eligibility` returns `{ patientId, exists, active, eligible }`. Missing patient is `404`; inactive patients exist but are not eligible.

Invalid patient input returns `400` with `{ "error": "Invalid patient input" }`; malformed input returns `400` with `{ "error": "Invalid request" }`. A missing patient returns `404` with `{ "error": "Patient not found" }`. See the root [API contracts](../../docs/api-contracts.md) for all service contracts.

## Configuration and database

| Environment variable | Default |
|---|---|
| `SERVER_PORT` | `8082` |
| `PATIENT_DATABASE_URL` | `jdbc:sqlite:./patient-service.db` |

Hibernate `ddl-auto:update` creates/updates the Patient-owned schema at startup. The SQLite file path is relative to the process working directory. Set `PATIENT_DATABASE_URL` to select another file. Patient tests use an in-memory SQLite database with `create-drop`; they do not use the development file.

## Tests

```powershell
mvn -pl services/patient-service test
```

The tests cover creation/retrieval, missing IDs, input validation, and inactive-patient eligibility.
