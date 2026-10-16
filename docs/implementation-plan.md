# Implementation Plan (Java / Spring Boot)

## Build order
1. Generate the four Spring Boot applications (Java 21, Maven) in their respective `services/*` folders.
2. Ensure each service starts independently on its assigned port and has `GET /health`.
3. Implement Patient Service and seed fictional patient data.
4. Implement Availability Service and atomic reservation/release operations.
5. Implement Appointment Service, idempotency storage and state transitions.
6. Implement Notification Service with test-configurable delay/failure.
7. Add end-to-end and challenge scripts, tests, evidence, report and demo.

## Suggested package structure per service
```text
src/
  main/
    java/edu/assignment/<service>/
      <Service>Application.java
      controller/
      service/
      repository/
      model/
      dto/
      exception/
      config/
    resources/
      application.yml
  test/
    java/edu/assignment/<service>/
```

Keep controllers thin. Put business rules in service classes and database operations in repositories. Use DTOs for API contracts rather than exposing JPA entities directly.

## Dependencies
Common Spring dependencies:
- Spring Web
- Spring Data JPA
- Validation
- Spring Boot Test

Also add:
- SQLite JDBC driver and a Hibernate-compatible SQLite dialect (verify version compatibility)
- Springdoc OpenAPI starter for Swagger UI
- HTTP client support (Spring `RestClient` is available in modern Spring Framework versions)

Avoid adding a message broker, API gateway, frontend, authentication or a real email/SMS integration for this assignment.

## SQLite concurrency note
Use a database-level atomic conditional update inside a transaction and assert the affected row count. A Java `synchronized` block is not sufficient across separate application instances. Configure a sensible SQLite busy timeout and handle lock/busy errors. Run the required 20+ request script repeatedly.

## Idempotency note
Store `Idempotency-Key`, request fingerprint, operation state and final response in Appointment Service's database. Use a unique key and transaction. Concurrent duplicates must not create multiple appointments. If a request with the same key is already in progress, wait for its stored result or return a documented retryable response; do not create a second effect.

## Failure handling
Use finite HTTP timeouts and bounded retries with exponential backoff and jitter for notification delivery. Do not retry booking creation blindly without idempotency. If availability reservation succeeds but appointment persistence fails, attempt compensation using a release API and log the outcome. State the crash-window limitation in the report.
