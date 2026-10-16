# Challenge Scripts to Implement

Suggested scripts can be written in Java, Python or shell. Use the language the team can run reliably.

- `concurrency-test`: reset a capacity-1 slot, send 20+ concurrent booking attempts with different idempotency keys, assert exactly one succeeds and final capacity is 1.
- `idempotency-test`: submit same key/body sequentially and concurrently, verify one appointment effect; same key with changed body must return 409/422.
- `resilience-test`: force notification delay/failure, verify bounded retries, visible failure and confirmed booking without duplicate appointment.
- `e2e-booking`: submit booking and verify appointment, slot and notification state.

Print timestamps, parameters, counts and pass/fail assertions. Save real terminal output in the matching evidence folder.
