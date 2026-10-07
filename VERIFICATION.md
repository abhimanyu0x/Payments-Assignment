# Verification record

The implementation is not yet submission-ready: Docker-dependent runtime checks and the candidate video remain outstanding. A successful compilation is not proof of payment correctness.

## Executed in the authoring environment

| Check | Result | Evidence or scope |
|---|---|---|
| Java 21 Maven reactor package | PASS | Both app and mock-psp compiled and packaged |
| Invoice calculation unit tests | PASS | 3 tests: exact cents, upper bound, zero total |
| Webhook cryptography unit tests | PASS | 2 tests: encryption/tamper and signed body/timestamp |
| Receiver HTTP tests | PASS | 3 tests: invalid signature, duplicate ID, stale/future timestamp |
| Frontend TypeScript / Vite production build | PASS | Production assets generated; this is not browser or API verification |
| PostgreSQL migration syntax | PASS | V1, V2 and role bootstrap parsed with PostgreSQL syntax parser |
| Java SQL literal syntax | PASS | 35 complete SQL constants parsed after joining string literals and replacing bind placeholders; dynamic list query fragments excluded |
| OpenAPI 3.0.3 validation | PASS | Validated with openapi-spec-validator |
| Compose YAML and dependency references | PASS | Static parse only; no Docker startup claim |
| Python scripts | PASS | Compiled; receiver tests executed |

Java totals: 11 tests discovered, 5 passed, 6 skipped, 0 failures. This records the earlier run. The PostgreSQL integration class has since been changed to require Docker: missing Docker now fails the full test suite rather than skipping these tests. The receiver contributes 3 additional passing tests.

## Not executed or not verified

- Six real-PostgreSQL integration tests: concurrent invoice payment, accepted-response replay, timeout recovery, lost-success recovery, atomic rollback, same-key race across different invoices.
- Clean `docker compose up`, image builds, startup migration ordering and restart persistence. Docker is not installed in this execution environment.
- Actual mock PSP 30-second behaviour through the complete application and charge-count assertions in scripts/smoke.py.
- End-to-end database tenant isolation and API-key rotation/revocation.
- Browser visual inspection and user flows. Browser download returned an unavailable-site HTML response; no browser screenshot or successful visual review is claimed.
- CI workflow execution, capacity/load tests, and any 100x performance claim.
- Candidate video and signed-out access to its link.

## Required next verification

On a Docker-capable machine, run `./mvnw test` and confirm there are no skipped integration tests. Then run `docker compose --profile demo up -d --build` and `python3 scripts/smoke.py` from the repository root. The smoke script checks actual mock outcomes, exactly one processor operation/POST per accepted attempt in the tested scenarios, API replay, final states, and recent webhook deliveries. Use a fresh isolated demo volume for a clean-machine check; do not delete existing volumes without intending to lose their data.

Run the optional UI with the ui profile and inspect desktop/mobile flows against the real API. Correct any failures before replacing this record with stronger claims. Finally record the video and replace the README placeholder.

## Known design limitations

Local webhook registration is restricted to the configured destination. It is not a general production URL admission service. The mock's durable lookup/deduplication and deterministic 500 recovery are explicit extensions, not assumptions about real processors. Timestamp event pagination is historical browsing, not a guaranteed incremental stream. Full audit/observability, production rate limiting, real-provider guarantees, managed secrets, and automatic operator reconciliation remain outside this exercise.

## Follow-up verification attempt

The local environment has no Docker daemon, and installing system services is restricted. An isolated Vercel Sandbox was attempted as an alternative, but creation returned HTTP 403: permission to create the sandbox was denied. No remote verification ran. The temporary project creation returned an ID, but subsequent ownership lookup returned 404 and project listing was empty; no running sandbox or deployment was created.

The CI command now starts with `clean test`, and integration tests no longer opt out when Docker is unavailable. These changes prevent a successful unit-only run from being mistaken for full verification. Runtime and browser checks remain blocked, not passed.

The follow-up `mvn test` was executed: five unit tests passed, zero tests were skipped, and integration-class startup failed with `Could not find a valid Docker environment`. This confirms the missing-Docker safeguard, not integration correctness.
