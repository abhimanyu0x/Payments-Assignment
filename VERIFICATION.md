# Verification record

Executed on 7–8 October 2026 by Claude Code (an AI agent working in the author's checkout), on macOS 26 (arm64) with Docker Desktop (Engine 29.5.3). The author has not yet re-run these checks personally. Everything below was actually executed; anything not executed is listed under "Not verified".

## Current state (latest run, 8 October 2026)

Latest round, at the author's request: a full review against the assignment brief (including the UI), and fixes for everything found (DECISION_LOG D61 to D66).

| Check | Result |
|---|---|
| `./mvnw -B clean test` | **PASS**: 35 run, 0 failures, 0 skipped (app 29, receiver 6). New: demo key refused outside demo mode (3 unit tests), review settled by lookup without a charge, webhook endpoint rules, receiver body limit |
| `./mvnw -B spotless:check` | **PASS** |
| Fresh Compose stack (`--profile ui`, volumes recreated) | Healthy; `migrate` exits 0 with the `demo` context |
| README curl examples | Customer, invoice (3 × 1299 = 3897 cents) and payment work; the invoice ends `paid` with `payment_block_reason: invoice_already_paid` |
| Webhook rules (curl) | `https://169.254.169.254/x`, `http://example.com/x` and `https://10.1.1.1/x` → `422`; registering the demo receiver again → `409`; each event delivered once (`200`, 1 attempt) |
| `docker compose --profile e2e run --rm e2e` | **PASS**: 1/20 concurrent accepted; all tokens (`tok_timeout` 41.3 s); replay identical; one POST per attempt; 10 webhooks verified |
| UI (built-in browser) | Strict CSP and `X-Frame-Options: DENY` served; no CSP errors; `12.5` cents is blocked before sending; an invoice of 1250 cents shows `$12.50`; paying succeeds and the retry button disappears once paid |
| Defects found during this round | Finishing a reviewed payment did not clear `review_required` (database CHECK rejected it); the local UI proxy's `X-Forwarded-Proto` broke same-origin POSTs (403). Both fixed |
| `DESIGN.md` | 1,499 words; failure mode (b), webhook registration and `markPaid` wording now match the code |
| Second self-review (D67) | 9 more issues fixed; `./mvnw -B clean test` **PASS** again (35 tests, now also covering NAT64, unresolvable hosts and a lowercase `bearer` scheme) |
| Third self-review (D68) | `./mvnw -B clean test` **PASS**: 36 run (app 30, receiver 6); `spotless:check` **PASS**; UI builds; `docker compose --profile e2e run --rm e2e` **PASS** (`tok_timeout` 41.3 s, 10 webhooks verified) |
| Remaining limits (D69) | `./mvnw -B clean test` **PASS** (36); a 64 KB + 1 byte body → `413` with `request_id` matching `X-Request-Id`; `HTTPS://…` duplicate → `409`; `key-admin` with an unknown business prints `Unknown business …`; a 70 KB body against Compose → `413`; UI picker lists all customers; e2e **PASS** (`tok_timeout` 41.1 s) |
| CI start sequence | `docker compose up -d --build --wait` exits 0 with `migrate` exited (0) and all services healthy |
| README curl examples, run verbatim | Step 2 total `8897`; step 3 `202` with `Location`, attempt `succeeded`, invoice `paid`, same key replays an identical body, new key `409`; step 4 attempt `failed`/`card_declined`, invoice `open`, `invoice.payment_failed` and `invoice.created` delivered |

Previous round, at the author's request: Micrometer Tracing with UUIDv7 trace IDs, and every point from the codebase review (see DECISION_LOG D55 to D60).

| Check | Result |
|---|---|
| `./mvnw -B clean test` | **PASS**: 30 run, 0 failures, 0 skipped (app 25, receiver 5). `IdsTest` (3) left with `Ids`; two new tests added: UUIDv7 trace IDs, and body limit, page size and cursors |
| `docker compose --profile tests run --rm tests` | **PASS**: same 30 tests inside the Maven container, containers bootstrapped from `docker/init.sql` |
| `./mvnw -B spotless:check` | **PASS** (Spotless only reordered imports in three files) |
| Fresh Compose stack | Healthy. `migrate` exits with code 0 without the old exit helper |
| `docker compose --profile e2e run --rm e2e` | **PASS**: 1/20 concurrent accepted; all tokens (`tok_timeout` 41.3 s); replay identical; one POST per attempt; 10 webhooks delivered and verified |
| Trace IDs (curl against Compose) | `X-Request-Id: 01a11a3a48d07c18842d6c2fbf5d439e` (version digit 7). The `401` body carried the same value as `request_id`. A sent `traceparent` was ignored |
| Access log | `access … trace=01a11a3a48d07c18842d6c2fbf5d439e business=01a11810-ce4f-76f3-9863-cbf7566684b5 GET /api/v1/customers 200 45ms` |
| Cross-service trace | Trace `01a11a3c870773229da0cd4305611003` appears in the app's `job_done job=webhook.delivery` line and the receiver's `webhook_received` line |
| `key-admin` command from the README | Creates a key and exits |
| `openapi.yaml` vs previous round | Only additions: `limit` minimum 1 and maximum 100, a documented 422 on list endpoints, the two `payment_block_reason` values. `card_token` lost a redundant `maxLength` |
| Static scans | 0 code comments; no SQL strings, `@Query` or `JdbcTemplate`; no raw `== null` checks; no wildcard imports; no 2-space files |
| Defects found and fixed | Spring's `Enum.valueOf` fallback accepted `?state=OPEN` with a converter; replaced by a property editor (D58). The e2e container needed `-am` for the new module |
| Correction to the review | Item 46 was wrong: the Testcontainers 1.21.4 pin is needed for Docker Engine 29 (D22) |

Previous round, at the author's request: Spring Boot idioms and less boilerplate.

- **Repositories:** Spring Data `JpaRepository` interfaces with derived queries and `@Lock`. QueryDSL stays only in the `PaymentAttemptClaims` and `WebhookDeliveryClaims` fragments and `Pages`. The mock PSP and receiver no longer use QueryDSL.
- **Boilerplate:** Lombok builders and getters on every entity; `@RequiredArgsConstructor` and `@Slf4j` instead of hand-written constructors and loggers.
- **Configuration and HTTP:** validated `AppProperties` and `ReceiverProperties` replace `@Value`. `RestClient` replaces the custom HTTP helper.
- **Null and empty checks:** Spring `StringUtils`, `CollectionUtils`, `ObjectUtils` and `Assert`, plus `Objects` and `Optional`.
- **Tests:** an `IntegrationTest` base class with a `@ServiceConnection` container (`TestDatabase`). The own-database guard is inherited. The receiver test uses `@ServiceConnection` too.
- **Changelogs:** renamed to `create_table_<table>.xml`, with `data/insert_demo_business.xml` for the seed.

| Check | Result |
|---|---|
| `./mvnw -B clean test` | **PASS**: 31 run, 0 failures, 0 skipped (app 26, receiver 5) |
| `openapi.yaml` | Byte-identical to the previous round |
| Fresh Compose stack (volumes recreated) | Healthy. Liquibase applied 25 changesets from 12 files, all `create_table_*.xml` plus `insert_demo_business.xml`, author `Abhimanyu` |
| `docker compose --profile e2e run --rm e2e` | **PASS**: 1/20 concurrent accepted; all tokens (`tok_timeout` 41.7 s); replay identical; one POST per attempt; 10 webhooks delivered over `RestClient` and verified by the receiver |
| Mock PSP generated SQL (`SPRING_JPA_SHOW_SQL=true`) | `… where status=? and complete_after<=? for no key update skip locked` from the Spring Data `@QueryHints` method |
| Static scans | 0 code comments; no SQL strings, `@Query`, native queries or `JdbcTemplate`; no `@Value`; no hand-written getters on entities |
| Defect found and fixed | `@Transactional(propagation = MANDATORY)` on `WebhookEvents.record` made the Mockito spy stub fail through the proxy. Replaced by `Assert.state` on an active transaction; the rollback test is unchanged |

Previous round, at the author's request:

- **Changelogs without SQL elements.** The changelogs have no `<sql>`, `<where>` or view elements, so IntelliJ's "No data sources are configured to run this SQL" hint has nothing to flag. CHECK constraints are now appended to each `createTable` by `modifySql` `regExpReplace`. Keys and indexes moved to a second changeset per table, and the seed rollback is an explicit empty `<rollback/>`. 25 changesets in total; the database was recreated.
- **Tests go through the application.** All SQL and `JdbcTemplate` usage was removed from the tests:
  - **Behaviour:** driven through the HTTP API, `PaymentService`, the repositories and `PaymentProcessorClient`.
  - **Results:** asserted through the API and repositories.
  - **Rollback:** atomic rollback uses a `@MockitoSpyBean` on `WebhookEvents` instead of a database trigger.
  - **Lease expiry:** uses `app.payment-lease-seconds=0` instead of updating rows.
  - **Database rules:** proven by saving entities through repositories.
  - **Isolation:** the per-test `TRUNCATE` is replaced by fresh data plus draining leftover work through the service.
  - **Receiver test:** checks through the receiver's `/events` API.
  - **End to end:** checks `post_count` through the mock PSP's API instead of querying its table.
  - **Support code:** a small `BusinessEntity` and `BusinessRepository` were added for the second-tenant fixture.

| Check | Result |
|---|---|
| `./mvnw -B clean test` | **PASS**: 31 run, 0 failures, 0 skipped (app 26, receiver 5) |
| Scan of `app/src/test` and `webhook-receiver/src/test` for SQL keywords, `JdbcTemplate`, `queryFor*` and `DriverManager` | None |
| Scan of changelogs for `<sql`, `<where`, `<createView` | None |
| Fresh Compose stack | 25 changesets applied. 14 CHECK constraints present in `billing`, `payments`, `notifications` and `mock_psp`. |
| `docker compose --profile e2e run --rm e2e` | **PASS**: all tokens, replay, one POST per attempt (via `post_count`), 10 verified webhooks |
| Not verifiable here | The IntelliJ editor itself. The changelogs no longer contain SQL elements for the inspection to flag, but the IDE was not opened. |

Previous round, all data access moved from `JdbcTemplate` and SQL strings to **JPA entities with QueryDSL repositories** (OpenFeign QueryDSL 6.12, Hibernate 6.6) in all three modules. The author chose QueryDSL JPA over QueryDSL SQL after the trade-offs were explained.

- **Schema:** two changelogs changed, each with a new timestamp ID. `payment_attempts` lost `accepted_response` and `psp_operation_id`; the replayed 202 is rebuilt from the row, and the operation ID is the attempt ID. `events.payload` became `events.data`; the signed envelope is rebuilt from the row. The database was recreated.
- **IDs:** still generated by the database (`uuidv7()`), via `@GeneratedValue(IDENTITY)` and insert-returning.
- **Removed:** the old `Rows` map helper and the unused JSON method.

| Check | Result |
|---|---|
| `./mvnw -B clean test` | **PASS**: 31 run, 0 failures, 0 skipped (app 26, receiver 5). All payment-correctness tests pass unchanged in intent: concurrency, replay, timeout, lost success, rollback, same-key race, stale worker, exhaustion, tenancy, security, Liquibase catalog, UUIDv7, messages. |
| Generated locking SQL (`spring.jpa.show-sql`) | Worker claim: `… order by next_attempt_at, id fetch first ? rows only for no key update skip locked`. Invoice and attempt locks: `for no key update`. |
| Hibernate `ddl-auto: validate` | Passes in app, mock PSP and receiver against the Liquibase schema |
| Fresh Compose stack | Healthy; Liquibase 22 changesets |
| `docker compose --profile e2e run --rm e2e` | **PASS**: all tokens (`tok_timeout` 41.1 s), replay identical, one processor POST per attempt, 10 webhooks delivered and verified |
| `openapi.yaml` | Byte-identical to the spec generated before the refactor |
| Response shapes | Unchanged apart from known earlier changes |
| Keyset pagination via API | Walked 4 customers newest-first, one per page, no gaps or repeats |
| UI (browser) | Invoice detail shows payment history and per-invoice webhooks (Delivered, 1 attempt, 200) |
| Static scans | No SQL strings, `JdbcTemplate`, `queryFor*` or native queries in any `src/main`; 0 code comments. Tests still use `JdbcTemplate` for fixtures and catalog assertions. |

Previous round, at the author's request:

- **Liquibase as XML:** changelogs were rewritten in native XML: `createTable`, `addUniqueConstraint`, `addForeignKeyConstraint`, `createIndex` with descending columns, `insert`, and `modifySql` for partial indexes.
- **CHECK constraints:** these are the only `<sql>` blocks, which is the author's choice among the options offered. Open-source Liquibase 4.31 silently ignores the `checkConstraint` attribute; a new catalog test caught it creating zero CHECKs.
- **Bootstrap:** schemas and default privileges moved into the bootstrap scripts.
- **View removed:** the `billing.invoice_payment_availability` view is replaced by a `PaymentActivity` Java interface.
- **Database port:** PostgreSQL is published on `127.0.0.1:${DB_PORT:-5432}`, so IntelliJ can attach a data source and browse `databasechangelog`.
- **Test isolation:** each Spring test class asserts it uses its own throwaway container.

| Check | Result |
|---|---|
| `./mvnw -B clean test` | **PASS**: 31 run, 0 failures, 0 skipped. App: 26. Receiver: 5. |
| `liquibaseXmlCreatesChecksPartialIndexesAndUuidv7Defaults` | Check constraints per table: invoices 4, invoice_items 2, payment_attempts 4, webhook_deliveries 1, mock_psp.operations 3. The `one_unresolved_attempt` and `one_successful_attempt` unique indexes and the `delivery_due` index carry their `WHERE` predicates. Descending index columns are present. Every `id` defaults to `uuidv7()`. No views exist in billing. Setting `state='void'` is rejected by a check constraint. |
| `runsOnlyAgainstItsOwnThrowawayDatabase` (all 3 Spring test classes) | The datasource URL equals the Testcontainers URL, and the database is not `dodo`. |
| Fresh Compose stack (new volume) | Healthy. Liquibase: 22 changesets ("Update has been successful"), author `Abhimanyu` only, `DATABASECHANGELOG` read from the host through the published port. |
| `docker compose --profile e2e run --rm e2e` | **PASS**: all tokens, replay, one POST, 10 verified webhooks |
| Comments | 0 in code, 0 XML comments in changelogs |

Previous round's changes, all at the author's request:

- **Liquibase:** changelogs are now XML, one file per table. Every changeset has `author="Abhimanyu"` and a millisecond creation-timestamp ID.
- **Webhook results in the UI:** events and deliveries now store `invoice_id`, and deliveries also store `event_type`. `GET /webhook-deliveries` accepts `invoice_id`. The UI shows each invoice's webhooks, and a renamed Webhooks tab.
- **Messages:** every user-facing message was rewritten to short plain English with no internals, and centralized in `Messages.java`.
- **No JDK needed:** the tests and the end-to-end test also run through Docker (`--profile tests`, `--profile e2e`).

The database was recreated, because changeset IDs and columns changed.

| Check | Command | Result |
|---|---|---|
| Full suite without a local JDK | `docker compose --profile tests run --rm tests` | **PASS**: 27 run, 0 failures, 0 skipped. App: 23 (14 integration, `ApiDocumentTest`, 8 unit). Receiver: 4. About 35 s. |
| Full suite with a local JDK | `./mvnw -B clean test` | **PASS**: same 27 |
| End-to-end without a local JDK | `docker compose --profile e2e run --rm e2e` | **PASS**: `tok_timeout` resolved after 41.0 s; 10 webhooks delivered and verified; one processor POST per attempt, checked via a read-only `psp_user` query |
| Liquibase XML | `SELECT id, author, filename FROM databasechangelog` | 14 rows. IDs `20261008070643551` … `20261008070643605`, author `Abhimanyu`, files `*.xml` |
| New tests | `webhookResultsAreListedPerInvoiceWithTheirEventType`, `errorMessagesArePlainAndRevealNoInternals` | **PASS**. The delivery filter returns only that invoice, with `event_type`. Validation, missing-reference and bad-key messages contain no internals and none of `; : ( ) [ ] _ ' "` |
| Generated OpenAPI | `openapi.yaml` | Descriptions are the plain messages, e.g. "The service is busy. Please try again." Required text fields have `minLength: 1`, derived from `@NotBlank`. |
| UI (browser) | Bad key; open an invoice; Webhooks tab; empty invoice form | "Use a valid API key." The invoice shows "Invoice created" and "Payment failed", each Delivered on 1 attempt with response 200. The tab lists event, invoice, attempts, status and response. The empty form shows "Enter a description for each line item. Choose a customer. Enter a price for each line item. Choose a due date." |
| Comments | Java/TS/CSS tokenizer scan, `<!--` in changelogs, `#` in YAML/conf | 0 |

Found and fixed during this round:

- **Wrong message:** an empty line-item description said "A description is too long", because `@Size(min = 1)` shared the too-long message. `min = 1` was removed where `@NotBlank` already covers empty input.
- **Indistinguishable IDs in the UI:** short IDs showed the first 8 characters, which are the timestamp part of a v7 ID, so invoices looked identical. The UI now shows the last 8 characters, and local times instead of raw ISO timestamps.

## Java-only, PostgreSQL 18 and Liquibase round (8 October 2026)

The repository is now Java-only, apart from the optional React UI. The old Python receiver, smoke script and receiver tests were replaced, and the stack moved to PostgreSQL 18.6 with database-generated UUIDv7 IDs and per-table Liquibase changelogs. The old PostgreSQL 17 volumes for this repository (`dodo-payments_*`, `dodo-claude_*`) were deleted and recreated, as the author asked.

| Check | Command | Result |
|---|---|---|
| All unit and PostgreSQL 18.6 integration tests | `./mvnw -B clean test` | **PASS**: 25 run, 0 failures, 0 skipped. App: 21 (12 integration, `ApiDocumentTest`, 8 unit). `webhook-receiver`: 4. The `e2e` group is excluded from this build by design. |
| End-to-end against Compose (replaces `smoke.py`) | `docker compose up -d --build --wait`, then `./mvnw -B -pl app -Pe2e test` | **PASS**: 1 test, 51 s (output below) |
| Clean start on PostgreSQL 18.6 | `docker compose --profile ui up -d --wait` on fresh volumes | **PASS**: about 19 s to all healthy. Liquibase applied 14 changesets ("Update has been successful"). |
| Liquibase on restart | `down`, then `up --wait` | **PASS**: "Previously run: 14", nothing reapplied. Invoice, attempt and received-event counts unchanged. |
| Changelog tracking | `SELECT … FROM databasechangelog` | 14 rows, each mapped to its own file (`schemas.yaml`, `identity/businesses.yaml` … `data/demo_business.yaml`) |
| Database-generated IDs | `information_schema.columns`; `uuid_extract_version(id)` per table | Every `id` column defaults to `uuidv7()`. After the e2e run there were 0 non-v7 IDs in customers, invoices, items, attempts, events, deliveries and endpoints. The seeded business and API key IDs are v7. |
| `key-admin` | `docker compose run … --operation=create`, then `revoke` | New key ID is v7 and generated by the database (prefix = ID); the key works (200), revocation gives 401 |
| Database privileges | `psql -U receiver_user` / `information_schema.role_table_grants` | `receiver_user` can read `demo_receiver` but is denied `billing`. `app_user` has only SELECT on the availability view. |
| Java webhook receiver | Receiver stopped, invoice created, then started again; delivery row reset | Retries at +5 s and +30 s (`delivery_error`), then `delivered` (HTTP 200). The redelivery was logged `duplicate=true`. |
| UI (browser) | Connect, open invoice, pay `tok_success` | Paid / Succeeded. The UI's idempotency key in the database is version 7. |
| Comments | Tokenizer scan of Java/TS/CSS; `#` scan of YAML/conf/Dockerfiles | 0 |

End-to-end output:

```
concurrent tok_success: 1/20 accepted, paid, one processor operation PASS
tok_card_declined       -> failed    accepted in 0.005s, resolved after 0.5s, replay identical, one POST PASS
tok_insufficient_funds  -> failed    accepted in 0.006s, resolved after 1.1s, replay identical, one POST PASS
tok_timeout             -> succeeded accepted in 0.004s, resolved after 40.9s, replay identical, one POST PASS
tok_network_error       -> failed    accepted in 0.004s, resolved after 6.2s, replay identical, one POST PASS
webhooks: 10 events delivered and signature-verified by receiver PASS
```

The sections below are the history of earlier rounds. Where they mention `scripts/*.py`, Flyway, `postgres:17.6` or the `1111…` demo IDs, they describe the repository as it was at that time.

## Environment (first full verification round)

| Tool | Version used |
|---|---|
| Maven tests (local) | Homebrew OpenJDK 24.0.1 compiling with `--release 21` |
| Maven tests (parity run) | `maven:3.9.9-eclipse-temurin-21` container, Java 21.0.7 |
| Runtime images | `eclipse-temurin:21-jre` (Java 21.0.12), `postgres:17.6-alpine`, `python:3.12-alpine`, `nginx:1.27-alpine` |
| Testcontainers | 1.21.4 (upgraded from 1.21.3; 1.21.3 is known to fail against Docker Engine 29's minimum API version) |
| Python / Node | 3.13.5 / 25.9.0 locally; the UI image builds with Node 22 |

Compose ran as an isolated project (`COMPOSE_PROJECT_NAME=dodo-claude`, `APP_PORT=18080`, `RECEIVER_PORT=18090`, `UI_PORT=13000`), so no other stack or volume was touched.

## Results (first full verification round)

| Check | Command | Result |
|---|---|---|
| Java unit and PostgreSQL integration tests | `./mvnw -B clean test` | **PASS**: 15 run, 0 failures, 0 skipped (10 integration, 5 unit). Same result on Java 21.0.7 in a container. |
| Webhook receiver tests | `python3 scripts/test_receiver.py` | **PASS**: 3 tests (valid signature plus duplicate, bad signature, stale and future timestamp) |
| Image build | `docker compose --profile ui build` | **PASS** (app, migrate, mock-psp, frontend) |
| Clean start | `docker compose down -v` (isolated project), then `docker compose up -d --wait` | **PASS**: about 16 s to all healthy. db healthy, then `migrate` applied V1 and V2 and exited 0, then mock-psp healthy, then app healthy. No manual steps. |
| Restart persistence | `docker compose down`, then `up -d --wait` | **PASS**: invoice, attempt and event counts identical; Flyway reported "up to date"; receiver SQLite events persisted |
| End-to-end smoke | `python3 scripts/smoke.py` | **PASS** (output below) |
| OpenAPI | Generated by `ApiDocumentTest`; validated with `openapi-spec-validator openapi.yaml` (0.7.2) | **PASS**: OpenAPI 3.1; byte-identical across two generations |
| Frontend build | `npm ci && npm run build` | **PASS** |
| DESIGN.md length | `wc -w DESIGN.md` | 1,499 words including diagrams and table |

Final smoke output on a clean volume:

```
concurrent tok_success: 1/20 accepted, paid, one processor operation PASS
tok_card_declined       -> failed    accepted in 0.004s, resolved after 1.0s, replay identical, one POST PASS
tok_insufficient_funds  -> failed    accepted in 0.006s, resolved after 1.0s, replay identical, one POST PASS
tok_timeout             -> succeeded accepted in 0.004s, resolved after 40.5s, replay identical, one POST PASS
tok_network_error       -> failed    accepted in 0.005s, resolved after 6.2s, replay identical, one POST PASS
webhooks: 10 events delivered and signature-verified by receiver PASS
ALL SMOKE CHECKS PASS
```

The smoke script asserts the following:

- 202 acceptance in under 2 s.
- Exact final attempt status and failure code, and the final invoice state.
- `tok_timeout` takes at least 29 s, which proves the real 30-second mock path ran.
- Same-key replay returns an identical body.
- Exactly one `mock_psp.operations` row with `post_count = 1` per attempt.
- `409 invoice_already_paid` for a new key on a paid invoice.
- Every webhook for the run's invoices is `delivered` and present in the receiver's verified-event store.

The receiver only stores events whose HMAC and timestamp verify.

Database rows inspected after the run:

- **`tok_timeout`:** 4 processor HTTP calls (1 POST that timed out at 5 s, then 3 GET lookups) across 3 reconciliation rounds. The mock completed at 30.0 s.
- **`tok_network_error`:** 2 calls (POST returning 500, then a GET confirming `processor_error`).

### Webhook retries and deduplication (manual)

1. With the receiver stopped (`docker compose stop demo-receiver`), a new invoice's `invoice.created` delivery recorded `attempt_count = 1`, retry in 5 s, then `attempt_count = 2`, retry in about 30 s, both with `delivery_error`.
2. After `docker compose start demo-receiver`, attempt 3 was `delivered` (HTTP 200).
3. Resetting that delivery row to `pending` caused a redelivery, which the receiver logged as `"duplicate": true`.

### Security and tenant isolation (manual probe, 27/27 PASS)

A second business was inserted as an operator, and a key was issued with the documented `key-admin` command. Probed through the API:

- **Authentication failures, all 401 with the standard error shape:** missing key, malformed key, wrong secret, unknown prefix, `Basic` scheme.
- **Tenant B against tenant A's resources, all 404:** customer, invoice, attempt history, payment attempt, `POST /pay`, and creating an invoice for A's customer.
- **Tenant B's lists:** exclude A's customers, invoices, events, deliveries and endpoints. A's pagination cursor is rejected for B with 400.
- **Money and request validation:**
  - client-supplied `total_amount_cents` → 400
  - decimal or string cents → 400
  - missing `Idempotency-Key` → 400
  - unknown card token → 422
- **Secrets:** endpoint lists never include secrets.

Rotation: the old and new keys both worked during overlap. After `--operation=revoke` the old key returned 401 while the new one still worked. A revoke naming the wrong business changed 0 keys.

Database roles: `app_user` is denied the `mock_psp` schema, and `psp_user` is denied `billing` and `identity`.

Log scan of app, mock-psp and migrate logs after all of the above: no API key secret, `Bearer` value, webhook secret, or generated password.

### Browser (optional UI, built-in browser, real backend)

Desktop and 375 px mobile layouts were exercised:

- Connecting with an invalid key, then a valid one.
- Customer creation, with a validation error shown first.
- Invoice creation with two line items: the backend total of $88.97 displayed.
- Payment with `tok_timeout`, shown live as Pending, then Unknown, then Succeeded and Paid.
- Same-key replay: one attempt, `post_count = 1`.
- Paid invoice: no payment control.
- Events and Deliveries views.
- Rejection of a disallowed webhook URL.

## OpenAPI generated from code (follow-up, 8 October 2026)

At the author's request, the hand-written `openapi.yaml` was replaced by springdoc generation.

- **Typed responses:** controllers that returned untyped `Map`s now return typed response records, so the generated schemas are accurate.
- **Generated file:** `ApiDocumentTest` rewrites `openapi.yaml` from the running app, and CI fails if the committed copy is stale.
- **Test suite:** `./mvnw -B clean test` → 16 run, 0 failures, 0 skipped (the 15 above plus `ApiDocumentTest`).
- **Response-shape comparison:** the field names and JSON types of every endpoint and error case were captured before and after the refactor. Every response shape is identical. The one intended difference: an invalid `?state=` filter is now `400 invalid_request` ("Invalid value for parameter 'state'.") instead of `422`, because the parameter is a typed enum.
- **Smoke run:** `scripts/smoke.py` against the rebuilt stack: ALL SMOKE CHECKS PASS (`tok_timeout` resolved after 41.3 s).
- **Docs endpoints:** Swagger UI (`/swagger-ui.html`) and `/v3/api-docs.yaml` return 200 without an API key, and Swagger UI renders the spec.
- **Not verified:** the CI staleness check (`git diff --exit-code openapi.yaml`) has not run in GitHub Actions.

## Comment removal and security hardening (follow-up, 8 October 2026)

At the author's request, all comments and Javadoc were removed from the Java, TypeScript, CSS, Python, YAML, SQL and nginx sources. A tokenizer that leaves string literals intact did the removal; a re-scan finds none left. The Maven wrapper scripts (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`) keep their third-party Apache license headers, and the generated `openapi.yaml` keeps its "generated" header.

Spring Security was hardened as described in the README's Security section. Results:

- **Tests:** `./mvnw -B clean test` → 17 run, 0 failures, 0 skipped. The new `securityHeadersCorsMethodsAndUnknownPathsAreLockedDown` test asserts:
  - every security header
  - `401` with `WWW-Authenticate: Bearer` without a key
  - a JSON `401` on unknown paths
  - `403` for a foreign `Origin`, with no customer created
  - `400` for PUT, DELETE, PATCH, OPTIONS and TRACE
  - `400` for duplicate JSON keys
  - `401` for an oversized key
  - `/actuator/env` denied, while `/actuator/health` returns only `{"status":"UP"}`
  - Swagger UI served with its own CSP
- **Before the change, on the running app:** there was no CSP, Referrer-Policy, Permissions-Policy or COOP/CORP header. `GET /` returned Spring's default 403 body with `timestamp` and `path`. A POST with `Origin: https://evil.example` was accepted (201).
- **Compose:** rebuilt and healthy. `scripts/smoke.py` → ALL SMOKE CHECKS PASS.
- **Encryption key:** starting the app with an empty `ENCRYPTION_KEY` fails immediately ("Encryption key must contain 32 bytes").
- **Browser:**
  - Swagger UI renders with no CSP console errors, and authenticated GET (200) and POST (201) calls from it work.
  - The optional UI, through nginx (now forwarding `Host` with its port), connects and creates records (201).
  - A foreign-origin POST through the same proxy gets 403.

## ALL_CAPS enums and UUIDv7 identifiers (follow-up, 8 October 2026)

At the author's request:

- **Enums:** constants are now ALL_CAPS (`InvoiceState`, `PaymentStatus`, `WebhookDelivery.Status`). API and database values stay lowercase through `@JsonValue value()` and a strict `from(String)`.
- **Identifiers:** every server-generated ID is now a UUIDv7 from `Ids.newId()`, implemented to RFC 9562: a 48-bit Unix-millisecond timestamp, version 7, the RFC variant and 74 `SecureRandom` bits. That covers customers, invoices, line items, payment attempts (and therefore PSP operation IDs), events, deliveries, webhook endpoints, API key IDs and prefixes, and request IDs. PostgreSQL 17 cannot generate v7 natively (PostgreSQL 18 adds `uuidv7()`), so it is generated in Java with no new dependency. Existing v4 rows remain valid.

Results:

- **Tests:** `./mvnw -B clean test` → 21 run, 0 failures, 0 skipped.
  - New `IdsTest` (unit): version, variant, timestamp round-trip, millisecond ordering, uniqueness over 100,000 IDs.
  - New integration test: API-returned IDs are v7, and PostgreSQL `ORDER BY id` matches creation order. JSON still says `open` and `pending`; `?state=open` → 200 and `?state=OPEN` → 400.
- **A bug the test caught:** a String→enum converter alone was not strict, because Spring silently falls back to `Enum.valueOf`, so `OPEN` was accepted. The `state` query parameter is now parsed explicitly with `InvoiceState.from`. The OpenAPI document still lists `open`/`paid`, taken from the enum.
- **Generated spec:** `openapi.yaml` contains no uppercase constant names.
- **Response shapes:** the before/after comparison on the rebuilt stack is identical to the original baseline, except the known `?state=` 400 change.
- **Smoke run:** `scripts/smoke.py` → ALL SMOKE CHECKS PASS.
- **Database:** every customer, payment attempt and event created during the run has a version-7 ID (6/6, 6/6, 12/12).

### Follow-up audit: string-typed statuses converted to enums

When the author asked whether everything was converted, a re-audit found statuses still handled as plain strings in core paths: payment finalization (`result.status().equals("succeeded")`), the processor client's result, invoice state checks, webhook delivery status updates, event types, and one value in the mock PSP. These now use enums:

- `PaymentProcessorClient.Result` carries a `PaymentStatus`. The PSP's wire strings are mapped only in `parse()`.
- `PaymentService.finish` uses `PaymentStatus` and `InvoiceState`.
- New `EventType` enum: `INVOICE_CREATED`, `INVOICE_PAID` and `INVOICE_PAYMENT_FAILED`, with wire values `invoice.created`, `invoice.paid` and `invoice.payment_failed`. The OpenAPI document now lists `event_type` as an enum.
- `WebhookWorker` uses `WebhookDelivery.Status`.
- The mock PSP uses a new `OperationStatus` enum.

No Java string literal for these states remains outside the enums, apart from the PSP protocol mapping in `parse()`. SQL text still uses the lowercase values, because those are the database values. Results:

- **Tests:** `./mvnw -B clean test` → 21 run, 0 failures, 0 skipped.
- **Smoke run:** `scripts/smoke.py` → ALL SMOKE CHECKS PASS.
- **Response shapes:** the before/after comparison is unchanged, apart from the known `?state=` difference.
- **Webhooks:** the receiver received `invoice.created`, `invoice.paid` and `invoice.payment_failed` with unchanged wire values.

Audit of IDs:

- **Generated IDs:** no `UUID.randomUUID()` and no SQL UUID generation remain in production code; all 10 generation sites use `Ids.newId()` (v7).
- **Seeded IDs kept:** the fixed demo IDs (`1111…`, `2222…`, `3333…`) in `V2__demo_business.sql` and `DemoEndpoint.java` are v4-shaped constants. They are kept so existing volumes and documented commands keep working.
- **Client-chosen values left as random v4:** idempotency keys and React list keys created by the UI, `scripts/smoke.py` and the tests. They are client values, not server identifiers.

## Defects found and fixed during verification

1. Spring Boot logged a generated in-memory user password on every start. Excluded `UserDetailsServiceAutoConfiguration`, since API keys are the only authentication.
2. Delivered webhook rows kept a future `next_attempt_at`. It is now set only while a delivery is `pending`.
3. Validation `details[].field` used Java camelCase (`unitAmountCents`). It now reports the JSON snake_case path (`items[0].unit_amount_cents`).
4. Compose had no app or PSP readiness checks. Added actuator healthchecks; the app waits for a healthy PSP, and the UI waits for a healthy app.
5. Plain `docker compose up` started no webhook receiver, so the seeded endpoint's deliveries could only fail. The receiver is now part of the default stack, and host ports are overridable.
6. UI defects:
   - An invalid key showed "Connected".
   - Field-level errors were hidden.
   - A rebuilt UI could load a blank page: `index.html` was cached and nginx served HTML for missing asset files.
   - A fourth nav item overflowed the mobile layout.
   - The token selector showed on paid invoices.
   - "Demo Business" was hard-coded for every tenant.
7. Testcontainers 1.21.3 was upgraded to 1.21.4 for Docker Engine 29 compatibility.

## Tests added

The six original integration scenarios were kept unchanged in intent; they were:

1. concurrent payments
2. same-key replay without a second POST
3. timeout recovery
4. lost success
5. atomic rollback
6. the same key racing across two invoices

New tests:

- **PSP 500:** treated as `unknown`, not a decline. Lookup then confirms `failed/processor_error`, and the invoice can be paid again with a new key.
- **Stale worker:** cannot overwrite a newer claim.
- **Exhausted recovery:** stays `unknown` with `review_required` and blocks a new charge.
- **Tenant isolation:** another tenant gets 404 and a revoked key gets 401.
- **Replay test extended:** asserts `invoice_already_paid` for a new key and `idempotency_key_conflict` for a changed body.

## Not verified

- **The GitHub Actions workflow** has not been run; there is no remote repository run.
- **Load or capacity:** nothing was measured. The 100× section of DESIGN.md is a plan.
- **Crash during a live Compose run:** killing the app container mid-call was not tested. Lost-success recovery is covered at the service level by an integration test.
- **Other platforms:** Windows (`mvnw.cmd`) and Linux hosts were not run.
- **Exhaustion timing:** the 10-minute payment-recovery and 1-hour webhook budgets were not waited out in real time. Exhaustion is covered with compressed delays in integration tests.
- **The demo video** has not been recorded, and the README link is a placeholder.

## Known limitations

- **Webhook URLs:** local registration accepts only the configured receiver URL; this is not a general SSRF defence. Registering it again yields a new secret that the bundled receiver does not know, so those deliveries are rejected and retried until exhausted.
- **Mock PSP contract:** deduplication by operation ID, the lookup endpoint, and the deterministic `processor_error` for `tok_network_error` are documented extensions in `docs/MOCK_PSP.md`, not guarantees of any real processor.
- **Event listing:** events are browsed by timestamp, which is not a gap-free incremental stream.
