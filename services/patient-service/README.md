# Patient Service

Spring Boot 3.4 / Java 21 service with its own SQLite database. The default database is `patient-service.db` in the process working directory. Set `PATIENT_DATABASE_URL` to use another SQLite JDBC URL. No database server is required.

## Run

From the repository root:

```powershell
mvn -pl services/patient-service spring-boot:run
```

The service listens on port 8082 by default. Override it with `SERVER_PORT`.

## API

Create a patient (the `active` field defaults to `true`):

```bash
curl -i -X POST http://localhost:8082/api/patients \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada Lovelace","email":"ada@example.com"}'
```

Creation returns `201 Created` with the patient and a `Location` header. `name` must be nonblank and at most 120 characters; `email` must be a valid email address and at most 254 characters. An optional `active` boolean sets the patient's status.

Retrieve a patient:

```bash
curl http://localhost:8082/api/patients/1
```

Check eligibility (a missing patient returns `404`; an inactive patient returns `eligible: false`):

```bash
curl http://localhost:8082/api/patients/1/eligibility
```

The eligibility response includes `patientId`, `exists`, `active`, and `eligible`. Invalid request bodies return `400`; missing patients return `404`. API errors contain a short message and no internal exception details.

Health check remains available at `GET http://localhost:8082/health`.

## Test

From the repository root:

```powershell
mvn -pl services/patient-service test
mvn -pl services/patient-service package
```

Tests use a named in-memory SQLite database and do not read or modify the development database file.
