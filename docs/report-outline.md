# Design Report Outline (maximum 15 pages excluding appendices)

1. Problem and scope: application overview, assumptions, BR1–BR6, out-of-scope features.
2. Service decomposition: responsibilities, data ownership, rejected alternatives.
3. Architecture: component diagram and sequence diagram with happy and failure paths.
4. Data design: schema/ER diagram per service, opaque cross-service IDs, no shared DB.
5. API design: endpoints, example requests/responses, error model, OpenAPI links.
6. Engineering challenges: concurrency, idempotency and Challenge 3A resilience; include design, alternatives, evidence and limitations.
7. Testing: unit, API, end-to-end and challenge test results.
8. Design decisions/trade-offs: SQLite vs server DB; synchronous REST vs broker; atomic reservation vs check-then-update; confirmed booking vs notification failure.
9. Limitations/future work: crash window, no auth/UI/real provider, no distributed ACID transaction; future reconciliation/outbox and containerisation.
10. Video and supporting document links.

Appendices: contribution table, half-page reflection per student, AI-use declaration.

Evidence folders must include the actual script, raw output, timestamped screenshot, final relevant state and a README explaining which claim each file supports. Do not fabricate evidence.
