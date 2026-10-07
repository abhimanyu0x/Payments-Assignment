# Video walkthrough checklist

Record 5–10 minutes in the required order. Keep explanations unscripted. This checklist is a navigation aid, not a claim of work already demonstrated.

## Architecture — 1 to 2 minutes

Open DESIGN.md. Show app, PostgreSQL schemas, HTTP mock PSP, and webhook receiver. Explain why Java fits your experience and why the invoice/attempt/event finalization stays in one local transaction. Show how the optional UI is removable.

## Live demo — 2 to 3 minutes

From a clean checkout run `docker compose --profile demo up` (explain that plain `docker compose up` starts the required core). Show migrations and API startup. Use README examples or `python3 scripts/smoke.py` to create a customer and invoices. Demonstrate successful payment on one invoice and tok_card_declined on another. Show attempt GET, invoice state, and signed deliveries in `docker compose logs demo-receiver`. The UI is optional, not required for this segment.

## State machine — 1 to 2 minutes, unscripted

Explain open and terminal paid. Why is unknown on the attempt rather than a new invoice state? Why does an open invoice sometimes reject payment? Why can a failed attempt permit a new key while an unknown attempt cannot? Explain omitted draft/void workflows and the scope trade-off.

## Failure case — 1 to 2 minutes, unscripted

Pick timeout or lost PSP success. Open:

- app/.../payments/PaymentService.java: accept, replay, finish transactions.
- app/.../payments/PaymentAttemptRepository.java: claim lease/version and indexes in V1 migration.
- app/.../payments/PaymentProcessorClient.java: lookup before resubmission using the same operation ID.
- mock-psp/.../MockPayments.java: durable operation, delay, conditional completion.

Explain what survives a restart and why a lease is not sufficient to prevent duplicate external charges. If demonstrating timeout, show 202 immediately, unknown after the HTTP deadline, and eventual paid after reconciliation. Explain the mock contract extension honestly.

## Before sharing

Run all Docker-dependent tests; check VERIFICATION.md. Replace README's pending video entry with the accessible link. Open the link in a signed-out browser. Re-read AI_USAGE.md and keep only accurate claims. Ensure DESIGN.md stays within roughly 800–1500 words. Do not claim hidden UI scope or unexecuted tests as completed evaluation work.
