# Data Ownership — One SQLite Database Per Service

No service may open or query another service's database. Cross-service references are opaque IDs only; do not create cross-service foreign keys.

## Patient Service — `patients.db`
`patients(id TEXT PRIMARY KEY, name TEXT NOT NULL, active INTEGER NOT NULL)`

## Availability Service — `availability.db`
`doctors(id TEXT PRIMARY KEY, name TEXT NOT NULL, specialty TEXT NOT NULL, active INTEGER NOT NULL)`
`slots(id TEXT PRIMARY KEY, doctor_id TEXT NOT NULL, start_time TEXT NOT NULL, capacity INTEGER NOT NULL, reserved_count INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)`
Optional: `reservations(id TEXT PRIMARY KEY, slot_id TEXT NOT NULL, appointment_id TEXT NOT NULL, status TEXT NOT NULL)`.

An atomic conditional update should increment reserved count only when the slot is available and `reserved_count < capacity`. Check the affected row count.

## Appointment Service — `appointments.db`
`appointments(id TEXT PRIMARY KEY, patient_id TEXT NOT NULL, doctor_id TEXT NOT NULL, slot_id TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL, correlation_id TEXT)`
`idempotency_records(key TEXT PRIMARY KEY, request_fingerprint TEXT NOT NULL, state TEXT NOT NULL, http_status INTEGER, response_json TEXT, appointment_id TEXT, created_at TEXT NOT NULL)`

## Notification Service — `notifications.db`
`notifications(id TEXT PRIMARY KEY, appointment_id TEXT NOT NULL, channel TEXT NOT NULL, status TEXT NOT NULL, attempt_count INTEGER NOT NULL, last_error TEXT, created_at TEXT NOT NULL)`

These are logical schema suggestions, not implemented tables. Adjust them to match the final code and report.
