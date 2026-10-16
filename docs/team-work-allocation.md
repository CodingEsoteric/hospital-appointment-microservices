# Team Work Allocation

## Member 1 — Team lead (you)
- Own architecture, API contracts and root README.
- Implement Appointment Service orchestration, idempotency and appointment states.
- Integrate all services and own end-to-end testing.
- Lead final report integration and demo.

## Member 2 — Patient Service
- Implement independent Spring Boot app on port 8082.
- Own `patients.db`, seed fictional patients and implement patient lookup/active status.
- Add `/health`, validation, OpenAPI docs and tests.

## Member 3 — Doctor & Availability Service
- Implement independent Spring Boot app on port 8083.
- Own `availability.db`, seed doctors/slots and implement atomic reservation.
- Add optional release endpoint for compensation, `/health`, OpenAPI docs and concurrency tests.

## Member 4 — Notification Service
- Implement independent Spring Boot app on port 8084.
- Own `notifications.db`, mock delivery and configurable delay/failure.
- Add `/health`, OpenAPI docs, tests and resilience evidence.

Every member must make meaningful commits, contribute to the demo, provide a contribution entry and write an individual reflection. Agree the JSON contracts before parallel development.
