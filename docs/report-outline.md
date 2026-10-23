# Design Report and Submission Outline

The report is limited to 15 pages excluding appendices. Describe the implementation that is present in the repository and distinguish it from proposed work. Use captured evidence as-is; identify its date/environment and do not describe an older capture as a fresh test run.

## Main report

1. **Problem and scope.** Summarize the four independently runnable Spring Boot services, booking flow, implemented business rules, and assumptions. State that appointment cancellation and a lifecycle/state machine are not implemented.
2. **Service decomposition and data ownership.** Explain each service's responsibility and its own SQLite file. Show that cross-service interaction uses REST APIs, not shared database reads. Include a component/architecture diagram.
3. **Booking sequence.** Diagram patient eligibility, conditional slot reservation, appointment persistence, notification request/retries, and reservation compensation if appointment persistence fails. Label the REST calls and failure paths.
4. **Data/API design.** Include per-service schema or ER views and implemented endpoints/DTOs from [API contracts](api-contracts.md). Document numeric ID types, error differences, and environment configuration.
5. **Business rules.** Use [business-rules traceability](business-rules.md), code locations, tests, and evidence. Do not include cancellation/state transitions as completed behavior.
6. **Challenge 1 — capacity concurrency.** Explain the conditional database update, SQLite busy timeout, concurrent request workload, final persisted state, and limitations of local SQLite. Reference `evidence/challenge-1-concurrency/` and distinguish automated test evidence from a running-service script if applicable.
7. **Challenge 2 — idempotency.** Explain the unique idempotency-key index, versioned canonical request fingerprint, changed-payload conflict, replay response, and two-instance database race test. Reference `evidence/challenge-2-idempotency/`.
8. **Challenge 3A — notification resilience.** State finite timeouts, transient-only bounded retries, exponential backoff/jitter settings, status/attempt count, replay behavior, and what the HTTP mocks verify. Reference `evidence/challenge-3a-resilience/` and identify mock-based evidence.
9. **Testing and evidence.** Record actual commands, Java/Maven versions, test counts, failures/errors, and capture dates. Explain test database isolation. Include the Notification persistence/restart evidence at `evidence/notification-persistence/`. Never promote a historical result to a fresh one.
10. **Design trade-offs and limitations.** Discuss SQLite vs server database, synchronous REST vs broker/outbox, compensation vs distributed transaction, simulated notification acceptance, response-loss duplicate requests, lack of external exactly-once delivery, and missing appointment lifecycle/cancellation API. Identify the crash windows and possible reconciliation/outbox work as future work.

## Appendices and presentation

- Component/architecture and sequence diagrams (main report sections above).
- Contribution table, with each person's actual work.
- Individual reflection for each team member.
- AI-use declaration following assignment requirements.
- Test/evidence index with links to genuine raw output and reproduction instructions.
- An 8–10 minute demo plan: introduce the architecture; show health checks and service ownership; create a patient, doctor, and slot; make a booking; demonstrate same-key replay and conflicting reuse; show capacity/concurrency or its captured evidence; demonstrate notification failure/retry and persisted status; close with limitations and team contributions. Assign speaking time to every member if required by the assignment.

Do not claim the repository contains report diagrams, contribution/reflection content, AI declaration, or a demo recording unless those artifacts are actually added. Evidence folders currently contain challenge READMEs and captured command output; verify any required scripts or screenshots separately before submission.
