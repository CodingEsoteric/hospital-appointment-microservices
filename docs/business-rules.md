# Business Rules and Traceability

| ID | Rule | Owning service | Test/evidence |
|---|---|---|---|
| BR1 | Only a known, active patient may book. | Patient + Appointment | Unknown/inactive patient rejected; no reservation left behind. |
| BR2 | A slot never exceeds capacity. | Availability | 20+ parallel requests; exactly N reservations succeed for capacity N. |
| BR3 | Same idempotency key + same payload returns original result with one effect. | Appointment | Sequential and simultaneous repeat test; one appointment record. |
| BR4 | Same idempotency key + different payload returns 409 or documented 422. | Appointment | Changed patient/slot request is rejected. |
| BR5 | Notification failure alone does not cancel a confirmed appointment. | Appointment + Notification | Force notification failure; booking remains confirmed and status is visible. |
| BR6 | Only allowed appointment state transitions are accepted. | Appointment | Invalid state transition is rejected. |

Suggested appointment state transitions:
`PENDING -> CONFIRMED`
`PENDING -> REJECTED`
`CONFIRMED -> CANCELLED`

Keep notification delivery state separate from appointment state, e.g. `PENDING`, `SENT`, `FAILED`.

Replace test paths with the actual code/test names after implementation. Do not claim rules pass before running their tests.
