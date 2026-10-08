# Invoice and Payment Service

A Java 21 / Spring Boot modular monolith on PostgreSQL, with a separate mock payment processor (PSP), signed and retried webhooks, and a small signature-verifying webhook receiver.

- `DESIGN.md` — the design document: data model, state machines, failure modes, webhooks, API keys, cuts, production gaps, scaling plan.
- `openapi.yaml` — API contract, **generated from the code** (see [API documentation](#api-documentation)); do not edit it by hand.
- `AI_USAGE.md` — how AI tools were used.
- `VERIFICATION.md` — exactly what was executed and the results.
- `DECISION_LOG.md`, `docs/MOCK_PSP.md`, `docs/VIDEO_GUIDE.md` — supporting notes.

**Language:** Rust was preferred by the brief; Java/Spring Boot was chosen because the author knows it well and can own and explain the code.

## Run

Requires Docker with Compose v2. From the repository root:

```sh
docker compose up
```

This builds the three Java services (app, mock PSP, webhook receiver) and starts PostgreSQL 18. A one-shot `migrate` container applies the Liquibase changelogs, then the mock PSP, the app and the demo webhook receiver start. No local JDK, Maven, Node or `.env` file is needed. The first build downloads dependencies and takes a few minutes. The app is ready when `docker compose ps` shows it `healthy` (or use `docker compose up -d --wait`).

| Service | URL (localhost only) |
|---|---|
| API | http://localhost:8080/api/v1 (health: `/actuator/health`) |
| Webhook receiver | http://localhost:8090/events (verified events), `docker compose logs -f demo-receiver` |
| PostgreSQL | `localhost:5432`, database `dodo` (for IntelliJ or psql; local demo credentials) |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html, raw spec at `/v3/api-docs.yaml` |
| Optional UI | http://localhost:3000 (start with `docker compose --profile ui up`) |

The mock PSP is not published to the host. All published ports bind to `127.0.0.1` only. Change them with `APP_PORT`, `RECEIVER_PORT`, `UI_PORT` and `DB_PORT`.

Seeded local-only values (from `db/changelog/data/insert_demo_business.xml` and `DemoEndpoint.java`):

- Business `01a11810-ce4f-76f3-9863-cbf7566684b5`, API key `demo.local-demo-secret-change-for-real-use`
- Webhook endpoint `http://demo-receiver:8090/webhooks`, signed with `DEMO_WEBHOOK_SECRET` from `docker-compose.yml` (the receiver gets the same value as `WEBHOOK_SECRET`)

`docker compose down` stops everything and keeps data. `docker compose down -v` also **deletes** the database volume, including the receiver's stored events.

## Database and migrations

- **Engine:** PostgreSQL 18.6.
- **IDs:** every primary key defaults to PostgreSQL's built-in `uuidv7()`, so IDs sort by creation time. Entities use `@GeneratedValue(strategy = IDENTITY)`, so Hibernate leaves the ID out of the insert and reads the database value back. Trace IDs are never stored, so the shared `platform` module generates them in Java (`UuidV7`), also as v7. See "Tracing and logs" below.
- **Data access:** **Spring Data JPA** repositories (`JpaRepository` interfaces). Simple reads are derived query methods such as `findByBusinessIdAndId`; row locks are `@Lock(PESSIMISTIC_WRITE)` methods. Queries Spring Data cannot derive (worker claims and guarded bulk updates) use **QueryDSL** in a repository fragment (`PaymentAttemptClaimsImpl`, `WebhookDeliveryClaimsImpl`). There are no SQL strings and no `JdbcTemplate` anywhere. Q-classes are generated at compile time by the QueryDSL annotation processor (OpenFeign fork 6.12, app module only) into `target/`, so none are committed.
- **Entities:** Lombok `@Getter @Builder` with a protected no-args constructor for JPA. There are no setters; state changes go through named methods such as `markPaid`, `claim` and `defer`. `created_at` and `updated_at` are filled by Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedDate`) from one injected `Clock` (UTC, microsecond ticks to match PostgreSQL). Entities whose ID is assigned by the caller (mock PSP operations, received webhooks) implement `Persistable`, so saving them is one `INSERT` with no extra `SELECT`.

| Module | Repositories |
|---|---|
| identity | `ApiKeyRepository` (tests add `BusinessRepository` for a second tenant) |
| customers | `CustomerRepository` |
| billing | `InvoiceRepository`, `InvoiceItemRepository` |
| payments | `PaymentAttemptRepository` (+ `PaymentAttemptClaims` QueryDSL fragment) |
| notifications | `WebhookEndpointRepository`, `EventRepository`, `WebhookDeliveryRepository` (+ `WebhookDeliveryClaims` fragment) |
| mock PSP | `OperationRepository` (Spring Data only) |
| receiver | `ReceivedEventRepository` (Spring Data only) |

- **Locking:** row locks use JPA `PESSIMISTIC_WRITE`. Worker claims add the lock-timeout hint for `SKIP LOCKED` (a QueryDSL hint in the app, `@QueryHints` in the mock PSP), so Hibernate generates `… FOR NO KEY UPDATE SKIP LOCKED`. Conditional updates (claim-version guards, the call counter, exhaustion sweeps) are QueryDSL `update` clauses.
- **Startup validation:** Hibernate runs `ddl-auto: validate`, so every entity is checked against the Liquibase schema at startup.
- **Lists:** keyset pagination uses Spring Data's scroll API (`QuerydslPredicateExecutor.findBy(...).scroll(...)`, returning a `Window`). The shared `Pages` helper only signs the cursor to the business and the list, so a cursor cannot be reused for another tenant or list. Page size is a validated `PageQuery` record (`limit` 1 to 100, default 20).
- **Derived values:** the replayed 202 is rebuilt from the attempt row, since it is always the same. The webhook body (`id`, `type`, `created_at`, `data`) is rebuilt deterministically from the stored event, so no row has to store its own ID.
- **Bootstrap:** `docker/init.sql` creates the roles, the seven schemas and their default privileges. PostgreSQL runs it once, on a new volume. Liquibase open source has no schema or grant change type, so these live here, and any table created later is granted to the right role automatically. The test containers copy this same file into PostgreSQL's init folder, so there is one bootstrap script.
- **Schema changes:** **Liquibase** XML, one file per table, under `app/src/main/resources/db/changelog/<schema>/`. Files are named for what they do: `create_table_<table>.xml` (for example `billing/create_table_invoices.xml`), and the seed is `data/insert_demo_business.xml`.

How migrations are picked up and run:

1. The `migrate` container starts the app with `MIGRATE=true`.
2. `spring.liquibase.change-log` in `application.yml` points to **`db/changelog/db.changelog-master.xml`**.
3. That master file `include`s each table file in order, from `identity/create_table_businesses.xml` to `data/insert_demo_business.xml`.
4. Liquibase runs every changeset not yet listed in the `DATABASECHANGELOG` table, then records it there.

The tests run the same master file against their own container.

- **Changeset format:** every changeset has `author="Abhimanyu"` and an `id` that is its creation timestamp to the millisecond (`yyyyMMddHHmmssSSS`, e.g. `20261008074530385`).
- **Native XML:** tables, keys, unique constraints, foreign keys and indexes use native change types (`createTable`, `addUniqueConstraint`, `addForeignKeyConstraint`, `createIndex`, `insert`), so Liquibase generates their rollbacks.
- **Partial indexes:** for example "one unresolved payment per invoice". These are `createIndex` with a `modifySql` that appends the `WHERE` condition.
- **CHECK constraints:** each table's CHECK rules are appended to its `createTable` by a `modifySql` `regExpReplace`. Open-source Liquibase has no CHECK change type and silently ignores the `checkConstraint` attribute. Keys, foreign keys and indexes therefore sit in a second changeset, so the replace touches only the `CREATE TABLE`.
- **No SQL elements:** the changelogs contain no `<sql>`, `<where>` or view elements.
- **Seed rollback:** the demo seed has an explicit empty `<rollback/>`; rolling back further drops the tables.

To add a table, create `create_table_<table>.xml` in the owning schema's folder with fresh timestamp IDs, and add one `include` line to the master changelog. Never edit a changeset that has already been applied to a shared database; add a new one.

**Seeing the database from IntelliJ.** PostgreSQL is published on `127.0.0.1:5432` (change with `DB_PORT`).

1. Add a PostgreSQL data source for host `localhost`, port `5432`, database `dodo`, user `postgres`, password `local_database_only` (local only).
2. You can now browse the `databasechangelog` table and every schema.
3. The changelogs contain no SQL elements, so IntelliJ's "No data sources are configured to run this SQL" hint no longer appears in them. It can still appear in the one bootstrap file, `docker/init.sql`, which is plain SQL by nature; attaching this data source clears it there too.

## curl examples

```sh
export API=http://localhost:8080/api/v1
export AUTH='Authorization: Bearer demo.local-demo-secret-change-for-real-use'
```

### 1. Create a customer

```sh
curl -sS $API/customers -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Aarav Sharma","email":"aarav@example.com"}'
```

Returns `201` with `id`. Copy it into `CUSTOMER_ID`.

### 2. Create an invoice (server computes the total)

```sh
export CUSTOMER_ID='<id from step 1>'
curl -sS $API/invoices -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"customer_id\":\"$CUSTOMER_ID\",\"due_date\":\"2026-11-15\",\"items\":[{\"description\":\"Consulting\",\"quantity\":3,\"unit_amount_cents\":1299},{\"description\":\"Setup\",\"quantity\":1,\"unit_amount_cents\":5000}]}"
```

Returns `201` with `"state":"open"` and `"total_amount_cents":8897`. A client-supplied total, a decimal or a string amount is rejected.

### 3. Pay it successfully

```sh
export INVOICE_ID='<id from step 2>'
curl -sS -i $API/invoices/$INVOICE_ID/pay -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pay-example-1' -d '{"card_token":"tok_success"}'
```

Returns `202 Accepted` immediately with `payment_attempt_id`, `"status":"pending"` and a `Location`/`status_url`. **202 means accepted, not paid.** About a second later:

```sh
curl -sS $API/payment-attempts/<payment_attempt_id> -H "$AUTH"   # "status":"succeeded"
curl -sS $API/invoices/$INVOICE_ID -H "$AUTH"                    # "state":"paid"
```

Repeating the same request with the same `Idempotency-Key` returns the identical 202 body without calling the PSP again. A new key returns `409 invoice_already_paid`.

### 4. A declined payment

Create another invoice (step 2), then:

```sh
export DECLINE_INVOICE_ID='<new invoice id>'
curl -sS -i $API/invoices/$DECLINE_INVOICE_ID/pay -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: decline-example-1' -d '{"card_token":"tok_card_declined"}'
```

Also `202`. The attempt becomes `"status":"failed","failure_code":"card_declined"`, the invoice stays `open` (payable with a new key), and `invoice.payment_failed` is delivered to the receiver. Other tokens: `tok_insufficient_funds`, `tok_timeout` (attempt is `unknown` after the 5 s PSP deadline and `succeeded` roughly 40 s after acceptance, via reconciliation), `tok_network_error` (PSP 500 → `unknown` → confirmed `failed` / `processor_error`).

Webhooks: `curl -sS $API/webhook-deliveries -H "$AUTH"` (add `?invoice_id=<id>` for one invoice; each row has `event_type`, `invoice_id`, `status`, `attempt_count` and `last_http_status`), `curl -sS $API/events -H "$AUTH"`, and `curl -sS localhost:8090/events`. In the optional UI, each invoice shows its webhooks, and the Webhooks tab lists them all.

## Tests

Only Docker is needed; no local JDK. `docker compose up` compiles inside a JDK 21 container, and the tests run the same way:

```sh
docker compose --profile tests run --rm tests
```

That uses the official Maven and JDK 21 image with your checkout mounted. It needs the Docker socket so Testcontainers can start `postgres:18.6-alpine` and apply the real Liquibase changelogs. It also rewrites `openapi.yaml` in your checkout. With JDK 21+ installed, `./mvnw -B clean test` does the same. The suite fails rather than skips when Docker is missing.

30 tests across the modules:

- 18 PostgreSQL integration tests in `PaymentIntegrationTest`, including security hardening, UUIDv7 ordering, enum wire values, per-invoice webhook results, plain error messages, the database rules, UUIDv7 trace IDs, the body size limit, page size checks and cursors
- 2 in `ApiDocumentTest`, which regenerates `openapi.yaml`
- 5 unit tests (invoice totals, secret encryption and webhook signing)
- 5 `WebhookReceiverTest` tests for the Java receiver: signature, duplicates, timestamp window and ID mismatch

**Tests use the application, not SQL.** No test contains a SQL query or uses `JdbcTemplate`.

- **Driving behaviour:** the real HTTP API (controllers, security, validation), `PaymentService`, the worker's claim in `PaymentAttemptRepository`, and `PaymentProcessorClient` against a controlled processor double (`FakeProcessor`, a small local HTTP server whose answers each test sets).
- **Checking results:** they read back through the same API and repositories.
- **Atomic rollback:** a `@MockitoSpyBean` makes the event module fail inside the payment transaction.
- **Lease expiry:** simulated with `app.payment-lease=0s`.
- **Database rules:** CHECK constraints, the one-unresolved-payment index and the `uuidv7()` defaults are proven by saving entities through the repositories and asserting the named constraint that rejects them.
- **Isolation:** each test uses fresh customers, invoices and idempotency keys. Due work left over from an earlier test is finished through `PaymentService` before the next one.
- **Shared setup:** app integration tests extend one base class, `IntegrationTest`. It holds the `@SpringBootTest` settings, turns tracing on as in production (`@AutoConfigureObservability`), imports `TestDatabase` (a `@ServiceConnection` PostgreSQL container bean, so no hand-written datasource properties), and provides the HTTP and fixture helpers (`send`, `get`, `customer`, `invoice`, `key`). Request bodies are built as maps, not escaped JSON strings. The PostgreSQL image comes from one Maven property, `postgres.image`. A test class adds only its own settings with `@TestPropertySource`.
- **Own database:** the base class's `runsOnlyAgainstItsOwnThrowawayDatabase` test runs in every subclass and asserts the connection is the test container, never the Compose `dodo` database. The receiver test does the same with its own `@ServiceConnection` container.
- **End to end:** only the opt-in `e2e` profile talks to the running stack. It checks "one processor POST per attempt" through the mock PSP's own API (`post_count`).

The three required by the brief:

| Requirement | Test |
|---|---|
| N concurrent `POST /pay`, at most one succeeds, no double charge | `concurrentRequestsAcceptOneCharge` (20 clients) |
| Same key replays same response, no second PSP call | `sameKeyReplaysOriginalResponseWithoutSecondPspCall` |
| PSP failure leaves no bad state | `timeoutIsUnknownThenRecoveredWithoutSecondCharge`, `processorErrorStaysUnknownUntilLookupConfirmsFailure` |

The others cover: lost success after a crash, atomic rollback when event persistence fails, the same key racing on two invoices, stale-worker writes, exhausted recovery, and cross-tenant/revoked-key access.

End-to-end against the real Compose stack, including the real 30-second `tok_timeout` (about one minute). `EndToEndTest` is tagged `e2e`, so the normal build skips it. It runs inside the Compose network, again with no local JDK:

```sh
docker compose up -d --build --wait
```

```sh
docker compose --profile e2e run --rm e2e
```

It creates records in the demo business and never deletes data. It checks processor operations with a read-only query as `psp_user`. The repository is Java-only apart from the optional React UI.

Optional UI build (Node 22): `cd frontend && npm ci && npm run build`.

## API documentation

The OpenAPI 3.1 document is generated by springdoc from the controllers, the request records and their validation limits, and the typed response records. There is no hand-maintained copy.

- **Live:** while the app runs, open http://localhost:8080/swagger-ui.html, click **Authorize**, paste the API key, and try requests. The raw spec is at http://localhost:8080/v3/api-docs.yaml.
- **File:** `openapi.yaml` in the repository is rewritten from the running app by `ApiDocumentTest` on every `./mvnw test`. CI fails if the committed copy differs, so commit it whenever an endpoint changes.

To change the docs, change the code: endpoints, records, `@Valid` constraints, or the few `@Operation`/`@ApiResponse` texts on `payInvoice` and `createInvoice`. Shared error responses and security are configured in `ApiDocumentation.java`.

## Security

Authentication is a bearer API key only, built from standard Spring Security parts: an `AuthenticationFilter` with `ApiKeyConverter` (reads and checks the header shape) and `ApiKeyAuthenticationProvider` (checks the key). Controllers receive the business with `@AuthenticationPrincipal UUID business`. All security errors are written by one `ErrorWriter`. The configuration (`SecurityConfiguration.java`) is locked down as follows.

**Authentication**
- `/api/**` requires the `BUSINESS` role, which only a valid, unrevoked API key grants.
- Only `/actuator/health` (read-only, status only) and the API docs are public. Every other path is denied with a JSON `401`.
- There is no form login, HTTP Basic, logout, remember-me, session or request cache.
- CSRF protection is off on purpose: no cookies are used, so there is nothing for CSRF to exploit.

**API key handling**
- Key hashes are compared in constant time, and unknown key prefixes cost the same as wrong secrets.
- If the key lookup itself fails (database down), the answer is `503`, not `401`.
- The `Authorization` header is limited to 256 characters.
- A `401` includes `WWW-Authenticate: Bearer`.

**Cross-origin requests**
- Any request carrying a foreign `Origin` is rejected with `403`, and no CORS allow headers are ever sent.
- The optional UI is same-origin through its nginx proxy.

**HTTP methods**
- Only GET, HEAD and POST are accepted. Anything else is rejected with `400` by Spring's strict HTTP firewall, which also blocks path-traversal and encoded-slash tricks.

**Response headers**
- Content-Security-Policy: `default-src 'none'` for the API, and a self-only policy for Swagger UI.
- `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, a restrictive `Permissions-Policy`, same-origin opener and resource policies, and `Cache-Control: no-store`.
- HSTS (one year, subdomains, preload) is sent on HTTPS requests.

**Input limits**
- Request bodies are limited to about 64 KB (`app.max-request-size`, enforced by Jackson's `maxDocumentLength`, answered with `413`), and headers to 8 KB, with a 10-second connection timeout. Jackson checks the limit as it reads input buffers, so a body can run up to one 8 KB buffer past the limit before it is rejected.
- JSON with duplicate keys, unknown fields, floats-as-integers or numeric strings is rejected.

**Errors**
- Error responses never include stack traces, exception names or framework messages.

**Secrets**
- There is no built-in encryption key: the app will not start without `ENCRYPTION_KEY` (32 bytes, base64). Compose supplies a local-only value.
- When running the app outside Docker (IntelliJ, `java -jar`), set it yourself, e.g. `ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=` for local use only.
- No key, secret or generated password is ever logged.

**Optional**
- Set `API_DOCS_ENABLED=false` to turn off Swagger UI and `/v3/api-docs`.

**Not built**
- Per-business rate limiting is not built; the brief scopes production rate limiting out (see DESIGN.md §7).
- Brute-forcing a key is not a practical concern, because each secret is 256 random bits.

## Tracing and logs

Every request and every background job gets a trace ID from **Micrometer Tracing** (OpenTelemetry bridge).

- **UUIDv7 only:** the `platform` module sets the trace ID generator, so every trace ID is a UUIDv7 written as 32 hex characters (the W3C trace ID format). It sorts by time like every other ID in the system.
- **Where it appears:** in every log line as `[app,<trace id>,<span id>]`, in the `X-Request-Id` response header, and as `request_id` in every error body. Give a client's `request_id` to an operator and they can find every log line for that request.
- **Across services:** the app passes the trace on to the mock PSP and the webhook receiver (`traceparent` header), so one payment can be followed through all three services' logs.
- **Callers cannot choose it:** the app ignores incoming `traceparent` headers (`management.tracing.propagation.consume: []`), so a client cannot inject a non-v7 or colliding trace ID.
- **Background jobs:** each claimed payment attempt or webhook delivery runs in its own trace and logs `job_done job=... id=...` when it finishes.
- **Access log:** Tomcat writes one line per request to the console with the trace ID and business ID, for example `access ... trace=01a11a3a48d07c18842d6c2fbf5d439e business=01a11810-... GET /api/v1/customers 200 45ms`. Turn it off with `ACCESS_LOG_ENABLED=false`.
- **JSON logs:** set `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` to switch the console to structured JSON; trace IDs become fields.
- No trace data is exported anywhere; the IDs exist for log correlation only.

## Messages

Everything a person or API client reads is short, plain English. That covers error messages, validation messages, API documentation and UI text. Messages never mention internals such as header names, the database, field paths or error codes, and avoid extra punctuation.

- **Where they live:** all of them are in one class, `app/src/main/java/dev/dodo/common/Messages.java`, which validation annotations, error handlers and the OpenAPI document all use.
- **Machine-readable codes:** `error.code` values such as `invoice_already_paid` are unchanged, so programs can still branch on them. The UI turns codes into plain sentences.
- **Logs:** they stay technical, with trace IDs, types and codes, because only operators read them.

## Configuration and secrets

Every value below is a **local demo value**. In production each would come from environment variables or a secret manager, and none would be committed.

| Value | Defined in | Used by | Stored as |
|---|---|---|---|
| PostgreSQL superuser password `local_database_only` | `docker-compose.yml` (`db.POSTGRES_PASSWORD`, `migrate.DATABASE_PASSWORD`) | Database container, Liquibase migration job, your IntelliJ data source (port bound to `127.0.0.1` only) | — |
| App role `app_user` / `local_app_only` | `docker/init.sql` (creates the role); default of `DATABASE_PASSWORD` in `app/src/main/resources/application.yml` | API | — |
| PSP role `psp_user` / `local_psp_only` | `docker/init.sql`; default in `mock-psp/src/main/resources/application.yml` | Mock PSP | — |
| Receiver role `receiver_user` / `local_receiver_only` | `docker/init.sql`; default in `webhook-receiver/src/main/resources/application.yml` | Demo webhook receiver | — |
| `ENCRYPTION_KEY` (AES-256 master key, base64 of 32 bytes) | `docker-compose.yml` (`app`, `migrate`). **No default** in `application.yml`: the app will not start without it. | Encrypts webhook signing secrets | Never in the database |
| Demo API key `demo.local-demo-secret-change-for-real-use` | Only its SHA-256 hash is in `app/src/main/resources/db/changelog/data/insert_demo_business.xml`. The plain key appears in this README and the tests. | Demo business | `identity.api_keys.secret_hash` |
| API keys you create | Printed once by the `key-admin` command | Clients | SHA-256 in `identity.api_keys.secret_hash` |
| Demo webhook signing secret (base64 `AQEB…AQE=`, 32 bytes) | `docker-compose.yml` only, defined once and passed to the app as `DEMO_WEBHOOK_SECRET` and to the receiver as `WEBHOOK_SECRET`. No default in either service. | Signing and verifying demo webhooks | AES-GCM ciphertext in `notifications.webhook_endpoints.secret_ciphertext`; never stored by the receiver |
| Webhook secrets for endpoints you register | Generated randomly and returned once by `POST /webhook-endpoints` | Your receiver | AES-GCM ciphertext, same column |
| Test-only values | `IntegrationTest`, `PaymentIntegrationTest`, `WebhookReceiverTest` | Tests (throwaway Testcontainers database) | — |

Non-secret settings are under `app.*` in `app/src/main/resources/application.yml`, each overridable by the environment variable in `${…}`. They bind to one validated `@ConfigurationProperties` record, `AppProperties`, so a missing or out-of-range value stops startup instead of failing later. The receiver has the same for `receiver.*` (`ReceiverProperties`).

Times are durations such as `150ms`, `5s` or `10m`; sizes are data sizes such as `64KB`.

- `PSP_URL`, `PSP_TIMEOUT`, `WEBHOOK_URL`, `WEBHOOK_TIMEOUT`
- `PAYMENT_LEASE`, `PAYMENT_RECOVERY`, `PAYMENT_RETRY_DELAYS`
- `WEBHOOK_MAX_ATTEMPTS`, `WEBHOOK_LEASE`, `WEBHOOK_DELIVERY_BUDGET`, `WEBHOOK_RETRY_DELAYS`
- `WORKERS_ENABLED`, `WORKER_POLL_INTERVAL`, `WORKER_CONCURRENCY`
- `MAX_REQUEST_SIZE`, `ACCESS_LOG_ENABLED`, `DEMO_SEED`, `API_DOCS_ENABLED`

The encryption key and the demo webhook secret are checked at startup (base64 of 32 bytes), so a bad value stops the app with a clear message. Shared HTTP client settings (`spring.http.client.*`: JDK client, 1-second connect timeout, no redirects) apply to both outgoing clients; each client sets only its own read timeout.

To run the app from IntelliJ or `java -jar` instead of Docker, set at least `ENCRYPTION_KEY`, plus `DEMO_WEBHOOK_SECRET` while `DEMO_SEED` is true.

Host ports are `APP_PORT`, `RECEIVER_PORT` and `UI_PORT` in `docker-compose.yml`.

## Code structure and design principles

Spring Boot does the plumbing so the code holds business rules only:

- **Spring Data JPA** repositories, auditing and the scroll API instead of hand-written DAOs, timestamps and paging; QueryDSL only for worker claims and guarded updates.
- **Spring Security** `AuthenticationFilter`, `AuthenticationConverter` and `AuthenticationProvider` instead of a hand-written filter, and `@AuthenticationPrincipal` in controllers.
- **`ResponseEntityExceptionHandler`** for all standard web errors, `ResponseEntity.created(...)` for `Location` headers.
- **Lombok** for entities (`@Getter @Builder`), constructor injection (`@RequiredArgsConstructor`, with `@Qualifier` copied through `lombok.config`) and loggers (`@Slf4j`).
- **`RestClient` beans** (`pspClient`, `webhookClient`) built from Boot's auto-configured builder, so they are traced and use the `spring.http.client.*` settings.
- **`@ConfigurationProperties`** records with `Duration` and `DataSize` values and startup validation instead of `@Value`.
- **Boot task executors** (`ThreadPoolTaskExecutorBuilder`) with graceful shutdown for the two workers; one `Jobs` helper claims and runs work for both.
- **Spring Security Crypto** (`AesBytesEncryptor` in GCM mode, `KeyGenerators`) for webhook secrets and API keys.
- **Spring utilities** for checks: `StringUtils.hasText`, `CollectionUtils.isEmpty`, `ObjectUtils.isEmpty`, `Assert`, plus `Objects` and `Optional`.
- **`@ServiceConnection`** Testcontainers, one test base class, and Spotless (`./mvnw spotless:apply`) for one code format: tabs, ordered imports, no unused imports.
- **A shared `platform` module**, used by all three services as a Spring Boot auto-configuration: UUIDv7 generation, UUIDv7 trace IDs, the `X-Request-Id` header, the `Clock`, the auditing time source, SHA-256 hashing and the enum wire-value helpers.

Design principles that keep it scalable:

| Principle | Where |
|---|---|
| Modular monolith, schema per module | `identity`, `customers`, `billing`, `payments`, `notifications`; modules share IDs, not JPA associations, so one can be split out later |
| Single responsibility | controllers only map HTTP; services hold rules; repositories only load and save; workers only schedule |
| Dependency inversion | `payments` depends on the `InvoicePayments` interface, and `billing` on `PaymentActivity`, not on each other's classes |
| Open/closed | a new status or event type is one enum constant; `WireValue` and `WireValueConverter` handle JSON and the database for all of them |
| DRY | one `Pages` for every list, one `Messages` for every user text, one `Errors` handler, one `IntegrationTest` base |
| Stateless API, horizontal scaling | no session state; any number of API and worker instances share work through `SKIP LOCKED` claims, leases and claim versions |
| Idempotency and exactly-once effects | idempotency key plus request fingerprint, a unique index, and one unresolved attempt per invoice |
| Database-enforced invariants | CHECK constraints, partial unique indexes and row locks, so rules hold even under races |
| Transactional outbox | events and webhook deliveries are written in the same transaction as the payment result |
| Fail fast | validated configuration, `ddl-auto: validate`, `Assert` on secrets and keys |
| Keyset pagination and UUIDv7 | stable cost per page, and time-ordered keys keep indexes compact |
| Defense in depth | API key hashing, strict headers, CORS rejection, request size limits, encrypted webhook secrets |
| Contract from code | `openapi.yaml` is generated from the controllers by a test |

## Optional UI

The brief lists a UI as out of scope. It was added on request to make demos easier, runs only with `--profile ui`, and can be deleted (`frontend/` plus its Compose service) without affecting the backend. It only renders API data: totals, eligibility (`allowed_actions`) and outcomes all come from the backend. The API key is held in tab memory.

## API key administration

There is no public sign-up. An operator creates or revokes keys:

```sh
docker compose run --rm --no-deps app \
  --spring.profiles.active=key-admin --spring.main.web-application-type=none \
  --app.workers-enabled=false --app.demo-seed=false \
  --operation=create --business-id=01a11810-ce4f-76f3-9863-cbf7566684b5
```

The key is printed once. To rotate, create a second key, switch clients, then revoke the old one with `--operation=revoke --key-id=<key id>` (same flags otherwise). Revocation takes effect on the next request.

## Demo Video

**Pending — the author will record it and insert the accessible link here before submission.**
