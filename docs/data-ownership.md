# Implemented Data Ownership

Each service owns one local SQLite database. Cross-service IDs are scalar values; there are no cross-service database foreign keys or direct reads of another service's file. Default SQLite JDBC URLs are relative to each process working directory. Set the corresponding `*_DATABASE_URL` variable to choose another path. Hibernate creates/updates tables on startup; Appointment, Availability, and Notification also initialize their unique indexes at startup.

## Patient Service — `patient-service.db`

Table `patients`: generated numeric `id`, `name`, unique `email`, and boolean `active`. `Patient` owns this data. Appointment reads eligibility through Patient Service HTTP.

## Availability Service — `availability-service.db`

- `doctors`: numeric ID, `name`, `specialty`.
- `appointment_slots`: numeric ID, `doctor_id` (Availability-owned doctor relation), `starts_at`, `ends_at`, `capacity`, `reserved_count`.
- `slot_reservations`: string UUID ID, `slot_id`, `released`, `created_at`.

`reserveIfAvailable` performs the conditional update against `capacity`; it does not load another service's data. SQLite busy timeout is configured to 5000 ms.

## Appointment Service — `appointment-service.db`

Table `appointments`: generated numeric ID, `patient_id`, `slot_id`, `reservation_id`, unique `idempotency_key`, nullable `request_fingerprint` (SHA-256 of canonical `booking-v1` request fields), `notification_status`, `notification_attempts`, and `created_at`. The unique idempotency index is installed by `IdempotencyConstraintInitializer`. Patient, slot, and reservation IDs are scalar references; this service does not query their databases directly.

There is no appointment lifecycle status or cancellation state stored in the current model.

## Notification Service — `notification-service.db`

Table `notification_events`: string `notification_id`, unique numeric `appointment_id`, numeric `patient_id` and `slot_id`, string `reservation_id`, `created_at`, `event_status`, and `accepted_at`. The unique appointment index is installed by `NotificationConstraintInitializer`. These are Notification-owned simulated acceptance records, not proof of external email/SMS delivery.
