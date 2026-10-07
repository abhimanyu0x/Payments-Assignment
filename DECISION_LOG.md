# Dodo Payments design decision log

Updated: 7 October 2026. Design discussion only; no application code or tests have run.

## Evidence labels

- User decision: explicitly selected or requested by the candidate.
- Accepted direction: assistant proposal followed by user agreement to proceed; not evidence of independent invention or testing.
- Proposed detail: still part of design, not yet implemented or independently verified.
- Verified: must name actual source inspection, test, command, or user confirmation. Reasoning alone is not an executed test.

## Decisions and attribution

| ID | Decision | Origin and status | Reason and trade-off | Verification still needed |
|---|---|---|---|---|
| D01 | Java and Spring Boot instead of Rust | Explicit user decision against initial assistant recommendation | User is confident in Java and new to Rust; prioritizes implementation ownership. Assignment strongly prefers Rust but permits another language with justification. | Build, tests, candidate code walkthrough |
| D02 | Optional modern UI, removable from submission | Explicit user request against assistant recommendation to omit UI | Easier exploration and demonstrations. Assignment explicitly excludes UI, so core submission must remain independently runnable and demonstrable. | Clean backend-only setup and optional UI setup |
| D03 | All authoritative business rules in backend | Explicit user requirement | Prevent divergent calculations, transitions, and payment decisions. UI still needs rendering, request, and interaction code. | API tests bypassing UI; frontend review |
| D04 | Modular monolith | User preference; accepted internal-interface approach after discussion of HTTP and sagas | Retain local atomic finalization; independent services are not required to add replicas. | Module dependency and transaction tests |
| D05 | One PostgreSQL instance, separate module schemas | Explicit user proposal | Make ownership visible while preserving local transactions. Schemas are not automatic security boundaries. | Composite foreign keys, role privileges, rollback tests |
| D06 | Local HTTP across process boundaries; Java interfaces within app | Accepted direction after user considered HTTPS and internal HTTP | One-command demo; avoid unnecessary internal network failures. Production TLS remains necessary. | Compose startup and HTTP integrations |
| D07 | No saga framework for initial implementation | Accepted assistant recommendation after user raised saga alternative | Only PSP needs external payment recovery; invoice, attempt, and event can commit locally together. | Crash recovery test |
| D08 | Concrete names and one owner per business rule | Explicit user requirement, clarified by assistant | Avoid repeated business logic without generic frameworks. SQL constraints may intentionally reinforce Java validation. | Code review |
| D09 | Async POST pay returning stored 202 acceptance | Assistant proposal; user agreed to continue discussing this flow | Avoid holding client request for 30-second PSP; latest state is available separately. | Same-response replay and status tests |
| D10 | Invoice states open/paid; attempt pending/unknown/succeeded/failed | Assistant proposal | Keep required scope small; unresolved is distinct from declined. | State and invalid-transition tests |
| D11 | Row locks, unique idempotency keys, partial unique unresolved-attempt index | Assistant proposal | Coordinate replicas and protect invariants in PostgreSQL. | Concurrent same-invoice, same-key, and different-invoice tests |
| D12 | Persisted payment work with leases and claim versions | Assistant proposal | Recover abandoned jobs and reject stale finalization. Lease alone does not prevent duplicate PSP calls. | Worker restart and stale-worker tests |
| D13 | Extend mock with durable operation deduplication and lookup | Assistant proposal and explicit spec extension | Recover success after lost response using stable attempt ID; preserve all specified token behaviours. | PSP restart, duplicate operation, and lost-response tests |
| D14 | Save immutable webhook event in payment finalization transaction | Assistant proposal | Avoid committed payment with lost notification; deliveries are at-least-once. | Atomic rollback, duplicate finalization, retry tests |
| D15 | Preserve unresolved status after reconciliation exhaustion | Assistant proposal | Retry exhaustion is not evidence of failed payment; do not allow unsafe new charges. | Exhaustion/review behaviour test |
| D16 | Persist mock card token for async worker | Assistant correction identified during schema design | Fingerprint alone cannot recreate PSP request. Store only named mock tokens; never raw real card data. | Worker processes persisted request after restart |

## AI use and corrections

ChatGPT read the uploaded assignment, proposed architecture and state machines, compared language and deployment choices, researched primary documentation, and drafted this log and proposed database model. Candidate has not yet claimed to have implemented or tested the proposals.

Actual candidate-origin choices currently available for AI_USAGE.md include Java, optional UI, and separate module schemas. Do not imply these were independent performance experiments. Their reasons are recorded above; final first-person wording requires candidate review.

Correction to the earlier AI proposal: the async flow specified a fingerprint but did not explicitly include a recoverable mock card token in the stored attempt. The schema proposal now includes mock_card_token. It has been corrected by reasoning, not yet verified by execution.

Another refinement: an invoice row lock does not serialize reuse of the same business-scoped idempotency key on different invoices. A database unique constraint plus rollback and fresh lookup handles that race. A targeted test is pending.

## Source verification already performed

- Complete text and tables of supplied DOCX inspected, including time budget, Rust preference, no-UI scope, required cases, tests, AI disclosure and video requirements.
- Official Spring Modulith documentation inspected for module interfaces.
- Official PostgreSQL documentation inspected for row locking and schema privileges.
- Official Docker documentation inspected for startup readiness.
- Official Microsoft architecture guidance inspected for saga trade-offs.
- Stripe documentation inspected as an example of provider idempotency; its guarantees must not be assumed for an unspecified PSP.

None of these source checks verifies the future implementation.

## Proposed database model

Schemas: identity, customers, billing, payments, notifications, mock_psp.
Tables: businesses, api_keys, customers, invoices, invoice_items, payment_attempts, webhook_endpoints, events, webhook_deliveries, mock_psp.operations.
Use UUID identifiers, bigint cents, integer positive quantities, timestamptz instants and date due dates. Text statuses with CHECK constraints keep migrations explicit. Composite tenant foreign keys prevent cross-business relationships. No cascading deletion of financial history.
Payment attempt stores business/invoice IDs, idempotency key, fingerprint, mock token, amount cents, stable PSP operation ID, current outcome, original acceptance response, PSP reference, failure/error fields, retry schedule, lease expiry, claim version, and timestamps.
Final schema, exact limits, worker retry schedule, secret handling, mock network-error reconciliation semantics, and endpoint-registration/delivery concurrency remain to be specified. Event reconciliation pagination must account for concurrent commits; timestamp ordering alone is not a no-missed-event guarantee.

## Deliverables planned

Source, optional frontend, mock PSP, SQL migrations, Dockerfile and docker-compose.yml, required tests, README.md, DESIGN.md (800-1500 words), AI_USAGE.md, API specification, demo commands and walkthrough checklist, this evolving decision log. User records the required video and supplies its accessible link. Never fabricate a video link or successful test result.

## API contract discussion

Status: assistant proposals, not yet implemented or tested. Prefix /api/v1. Authentication uses business-scoped bearer API keys; local HTTP only. Cross-business resource access returns 404 after authentication. No caller-selected business ID.

Routes: POST/GET /customers; GET /customers/{id}; POST/GET /invoices; GET /invoices/{id}; POST /invoices/{id}/pay; GET /payment-attempts/{id}; GET /invoices/{id}/payment-attempts; POST/GET /webhook-endpoints; GET /webhook-deliveries; GET /events. Optional UI-only POST /invoices/preview reuses exact invoice validation/calculation service without persistence. Infrastructure health endpoints are separate and disclose no secrets. API-key provisioning/revocation can use a documented admin command; no public business signup needed.

Accepted pay requests return 202 and Location. Store the response body and reconstruct replay-relevant headers; request IDs may differ on replay. Latest outcome comes from GET. Same-key conflicting body/invoice -> 409; paid invoice with new key -> 409; unresolved attempt with new key -> 409. Replay lookup precedes invoice-state rejection. Only accepted requests reserve idempotency keys; failed validation and pre-acceptance conflicts do not. No key expiry in assignment. Different-key retry after confirmed decline is a new attempt. PSP failures after acceptance are reflected in status, not a retroactive POST error.

Proposed input limits: JSON 64 KiB; 1-100 invoice items; description 1-500 characters; integer quantity 1-10000; integer unit_amount_cents 0-1000000000000; invoice total 1-1000000000000 cents; customer name 1-200 and email <=254 characters; key 1-128 visible ASCII characters. Monetary JSON fields reject strings, decimals, and exponent notation to keep integer-only request contract. Unknown request fields rejected, including client total/business ID. Currency fixed USD, returned but not supplied. Due date ISO calendar date; past dates allowed and no automatic overdue state. Java checked arithmetic remains mandatory even with input limits. Money range also stays within JavaScript safe integers; UI performs no money calculation.

Use consistent errors with code, message, request_id and optional field details. 400 malformed/missing/unknown request components; 401 invalid/revoked credentials; 404 scoped resource missing; 409 business conflict; 422 valid JSON with invalid field values; 503 database unavailable before acceptance. Hide raw SQL/stack traces/secrets. No claim that every infrastructure failure can be translated if process crashes.

Lists: limit default 20, max 100; opaque cursor bound to business, filters, and ordering; created_at plus id stable ordering; no total counts by default. Events list is historical retrieval, not a guaranteed no-gap incremental stream under concurrent commits. Reconciliation needs repeat/overlap scans with event-ID deduplication and invoice-status checks; a bounded overlap alone is not a mathematical no-gap guarantee. A committed-sequence feed or snapshot mechanism would be a separate stronger guarantee.

Invoice responses may include backend-owned allowed_actions plus payment_block_reason. Reuse the same payment eligibility implementation as POST pay; actions are advisory snapshots and POST always rechecks atomically. UI creates/preserves an idempotency key per user action and never decides financial success from HTTP timeout.

Deliverables will include OpenAPI YAML matching actual implementation, not just these proposals. Remaining: precise worker budgets, secret protection, webhook URL policy and event subscriptions, mock PSP recovery contract, and tests.

## External dependency contract discussion

Status: proposed design, not execution-verified. Mock PSP: POST /payments with operation_id=attempt UUID, integer amount_cents, USD currency, card_token; GET /payments/{operationId}. A duplicate operation with matching fingerprint returns the same durable operation; mismatched parameters return 409. Unique operation ID and transactional finalization create at most one confirmed charge per operation. No automatic retention expiry in demo. Separate DB role and schema; app uses HTTP only.

Token behaviours preserved: success and confirmed declines after about 100ms; timeout creates a durable pending operation, waits until its persisted complete_after (30s from first acceptance), then succeeds; network_error returns HTTP 500. Explicit extension for deterministic network-error recovery: persist terminal failed operation with processor_error before sending 500, and GET later returns that authoritative failed outcome. App must not infer failure directly from 500 or branch on token names. If response/state lookup is unavailable, outcome stays unknown. These extensions are documented, not presented as supplied PSP guarantees.

Refinement to earlier provisional 35-second worker timeout: propose 5-second total deadline per PSP HTTP call, 1-second connect timeout within it, 60-second payment lease. This deliberately exercises unknown-to-success reconciliation for tok_timeout while returning API 202 immediately. Persist delay independent of disconnected caller; short mock background scan completes due operations and GET may also complete due operations transactionally. Never hold DB transaction during sleep. Cancellation must not undo persisted PSP work.

Initial charge followed by at most six reconciliation rounds; delays after preceding round: 5,10,20,40,60,120 seconds. Each round GETs operation; terminal outcome finalizes, pending remains unresolved, 404 permits resubmission of identical operation ID/body under mock durable deduplication contract. Lookup errors never authorize a new ID. End automatic recovery after six rounds or ten minutes elapsed since acceptance, whichever first; then unknown/review_required=true and no new invoice attempt. Worker checks deadline before claim/send; prolonged downtime means actual review flag update can happen later on recovery. Claim counter distinct from reconciliation round counter and processor HTTP call count; add reconciliation_round_count and recovery_deadline_at to schema. Same-key replays remain valid; manual arbitrary mark-failed is not allowed. Process bounded batches/concurrency; no claimed backlog exceeding available worker slots.

Webhook payload contains event ID, type, created_at, invoice snapshot and attempt ID where applicable. HMAC-SHA256 over UTF-8 ASCII decimal delivery timestamp, '.', exact body bytes. Headers X-Webhook-Id, X-Webhook-Timestamp, X-Webhook-Signature v1=<hex>. Event ID is also in signed body and must match header. Constant-time signature verification, absolute timestamp skew <=300 seconds, receiver atomically deduplicates event ID with its processing. Retries use fresh timestamp/signature and same immutable event content. No delivery ordering promise; failed attempt event may arrive after paid event. Receiver checks current resource when needed.

Six webhook attempts: initial immediately then delays 5s,30s,120s,600s,1800s after failure; sum 2555s plus up to30s request time = approx43m5s absent scheduling delay. 5-second total request deadline, 30-second lease. Claim reserves attempt number before send; crashes may consume attempts without network send. Add delivery_deadline_at=created_at+1hour; stop claiming once exhausted or deadline reached. Downtime can defer marking exhausted, but sends do not continue past deadline checks. Retry every non-2xx and transport failure, no redirects. Read bounded response data, do not persist untrusted response bodies. Retain exhausted deliveries/events; reconcile through API; no automatic invoice rollback or new charge. At-least-once delivery, not exactly-once or guaranteed delivery within budget.

Webhook secret proposal: random 32 bytes, one-time return, AES-GCM encrypted at rest with unique nonce and master key outside database. Demo master key is explicitly local-only; production key management and rotation remain documented gaps. Signature uses decoded secret bytes; exact encoding documented. Local registration restricted to configured exact demo receiver destination, redirects off; arbitrary internet receiver support needs URL/DNS/egress controls and production HTTPS, not a simple scheme check. Endpoints immutable in minimal scope. Event transaction creates jobs for endpoints active and visible when its endpoint-selection query executes; concurrent registration may begin with next event. No automatic historical backfill.

Required verification: lost client response and PSP restart preserve tok_timeout operation; stable PSP ref and one successful operation on duplicate requests; 500 never directly sets failed; lookup resolves deterministic mock processor_error; exact raw-body tamper, timestamp, duplicate receiver and out-of-order event handling; transient receiver failure leads to retry without affecting invoice. Document no tests have run yet.

## Implementation structure and delivery plan

Status: proposed implementation plan, no application code/tests run. Java 21; pin an exact compatible stable Spring Boot release at implementation after dependency/build verification (do not claim latest is needed). Maven wrapper with root aggregator; app and mock-psp executable modules; optional frontend and demo receiver outside core business code. App packages organized by identity/customers/billing/payments/notifications, plus narrowly technical configuration/error handling. Dependencies: payments -> billing/customer-independent payment interface and notifications; billing -> customers and notifications; customers -> identity context as needed; notifications never imports billing/payment implementation. Shared technical code cannot become a common business-rules bucket.

Use Spring MVC, Spring JDBC/PostgreSQL driver, Flyway, validation, Spring Security for API-key filtering, JUnit/Testcontainers for PostgreSQL integration tests, existing Java HTTP client and crypto APIs where adequate. No JPA, Redis, broker, saga engine, generic service/repository base class, Lombok or mapping framework solely for boilerplate. No extra interface without a real module/dependency boundary. Prefer Java records for immutable API DTOs.

TransactionTemplate proposed for short write operations: explicit acceptance, finalization, job claims and invoice creation. Same DataSource/transaction manager across application schemas. Billing and event module calls join caller transaction (no REQUIRES_NEW, async hop or PSP call inside transaction). Worker coordinator remains nontransactional. This makes rollback and fresh lookup after unique violation explicit and avoids self-invoked @Transactional proxy pitfalls. Read committed with scoped row locking and constraints; invoice then attempt lock order. Claim transactions finish before execution/finalization. Integration test verifies invoice/attempt/event all roll back on injected finalization failure.

Worker starts with 4 payment slots, 4 webhook slots, app DB pool 10 and PSP pool 5 as tunable initial values, not measured capacity. One-second polling; claim no more than available slots; no transaction during network I/O or backoff. Deadlines must cover response reads, not just TCP connect. Graceful stop claims, finish bounded in-flight work or leave recovery to leases. Logs request_id/business_id/invoice_id/attempt_id/event_id as relevant without secrets. API readiness depends on DB and migrations, not continuous PSP health; temporary external outages are represented as durable work.

Compose plan: PostgreSQL; one-shot migrations/bootstrap; app; mock PSP; optional ui-profile frontend and demo-profile webhook receiver. Database health before migration; app/PSP start after successful migration. Readiness checks and retry-on-start application connection handling. Seed is idempotent and includes demo business/key; optional receiver signing setup uses explicitly local-only demo secret and matching endpoint seed. No manual migration, .env copying, JDK/Maven/Node installs required for standard Docker path. Multi-stage Dockerfile targets app/mock; frontend separately built only when selected. UI reverse proxies /api to app and does not embed API keys. Only intended app/UI localhost ports exposed by default; PostgreSQL and PSP stay internal. Commands intended: docker compose up; docker compose --profile ui --profile demo up; backend tests via Maven wrapper with Docker available. Exact test command verified during build. Clean-volume startup plus restart without duplicates tested.

Implementation slices: (1) core skeleton/migrations/Compose; (2) auth/customer/invoice including created event; (3) durable mock PSP; (4) payment acceptance/replay and worker including required concurrency/idempotency/timeout tests; (5) webhook signing/retry with demo receiver; (6) clean-start verification and final docs; (7) optional UI as separately accounted extension. Cut optional extra features/tests before exceeding core time budget; if 4-6 focused implementation hours exceeded stop and honestly document remaining work per assignment. No promise all expanded discussion scope fits that budget.

Delivery: source archive or repo-ready tree, pinned dependencies and lockfiles, app/mock and optional UI, SQL, Dockerfile/Compose, focused automated tests, README with run/demo commands and pending video link placeholder until user supplies real URL, DESIGN 800-1500 words, AI_USAGE preserving attribution, OpenAPI, DECISION_LOG, walkthrough checklist for unscripted user video. Verification report identifies passed/failed/not-run checks without fabricated claims. Next step is consolidate concise design and begin implementation; no repeated permission gate required.


## Implementation checkpoint

Application source, mock PSP, optional React frontend, migrations, Docker setup, API specification, documentation and focused tests have now been generated by ChatGPT. Candidate video and candidate-run verification remain pending.

Implemented refinements: Spring Boot 3.5.7 and Java 21 are pinned for the compiled build; Maven wrapper 3.9.9; React/TypeScript dependencies have a lockfile. Invoice eligibility uses one implementation; a read-only Billing reporting view exposes unresolved attempt availability as a deliberate cross-schema read dependency. All financial writes stay behind owning module services. Event payload is persisted as exact JSON text so the transmitted bytes remain stable; JSONB was only an earlier proposal. Operator key creation/revocation uses a separate application command profile. Optional UI renders backend-formatted amounts.

AI code review corrected a mismatched demo signing-secret length, a webhook worker permit-release error, and stale frontend list-response handling. Null line items are rejected explicitly. Source code was formatted for readability. These corrections are attributed to AI review, not claimed as candidate discoveries.

Executed evidence: Java reactor compilation/package succeeded; 5 Java unit tests and 3 standalone HTTP receiver tests passed. Six PostgreSQL integration tests compiled but were skipped because Docker is unavailable. Frontend production build passed, PostgreSQL migrations/35 complete SQL constants were syntax-parsed, OpenAPI validation passed. None of these replaces the pending Docker/real-database/end-to-end tests. Browser download failed and visual inspection is not claimed. Exact limitations and commands are in VERIFICATION.md.

Final artifact is a source package with README, DESIGN (1376 words at this checkpoint), AI_USAGE, OpenAPI, this decision history and video guide. No video URL, CI pass, clean Compose run, load result or candidate testing has been fabricated. Review the requirements and actual verification before submission.

## Verification follow-up

- Full Java tests must fail when Docker is missing; removed Testcontainers' automatic skip option. Local unit-only runs must be requested explicitly. CI uses clean test reports.
- The requested final runtime verification is blocked by the execution environment. An alternate isolated sandbox returned permission denied (HTTP 403); it did not run tests.
- No claim is made that only the video remains: database integration, Compose and browser checks still need a Docker-capable execution environment. This is an execution blocker, not a verified outcome or a candidate-approved trade-off.
