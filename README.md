# Invoice and Payment Service

A Java 21 / Spring Boot modular monolith with PostgreSQL, a durable mock payment processor, signed webhook delivery, and an optional React console.

**Verification status:** Java compilation/package and local unit checks have run. Real PostgreSQL integration tests require Docker. The full test suite now fails when Docker is unavailable instead of silently skipping these checks. Read `VERIFICATION.md` before treating this as submission-ready. The required video still needs to be recorded and linked.

## Run

Install Docker with Compose, then from this directory:

```sh
docker compose up
```

Compose builds both Java applications, starts PostgreSQL, applies Flyway migrations, and seeds a local business and API key. No local JDK, Maven, Node, `.env` copy, or manual migration is required. The first build downloads dependencies. The API listens on http://localhost:8080; readiness is http://localhost:8080/actuator/health. Wait for the application startup message before sending requests.

For the optional UI and verified-signature demo receiver:

```sh
docker compose --profile ui --profile demo up
```

Open http://localhost:3000. Enter the demo API key below. The key stays in tab memory, not the frontend bundle. Receiver events are visible at http://localhost:8090/events and in `docker compose logs demo-receiver`. API and receiver ports bind only to localhost; database and PSP ports are internal.

Local-only seeded values:

- Business: `11111111-1111-4111-8111-111111111111`
- API key: `demo.local-demo-secret-change-for-real-use`
- Webhook endpoint: `http://demo-receiver:8090/webhooks`
- Signing secret: base64 encoding of 32 bytes each equal to 1; see `demo/receiver.py`.
- Database credentials and encryption key in Compose are deliberately local demo values, not production secrets.

A seeded endpoint exists even without the receiver profile. Those deliveries retry and can exhaust until the receiver is started. Do not register another endpoint for the same demo receiver unless you also configure it with that endpoint's newly returned secret.

Stop using `docker compose down`. Data persists. **`docker compose down -v` deletes demo data**; use it only when intentionally resetting the exercise.

## Four curl examples

Use a POSIX shell. Replace returned UUID placeholders; these are request examples, not extra startup steps.

### 1. Create a customer

```sh
export API_KEY='demo.local-demo-secret-change-for-real-use'
curl -sS http://localhost:8080/api/v1/customers \
  -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' \
  -d '{"name":"Aarav Sharma","email":"aarav@example.com"}'
```

### 2. Create an invoice

```sh
export CUSTOMER_ID='<customer id from example 1>'
curl -sS http://localhost:8080/api/v1/invoices \
  -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"customer_id\":\"$CUSTOMER_ID\",\"due_date\":\"2026-10-20\",\"items\":[{\"description\":\"Consultation\",\"quantity\":2,\"unit_amount_cents\":2500}]}"
```

### 3. Accept a successful payment

```sh
export INVOICE_ID='<invoice id from example 2>'
curl -i http://localhost:8080/api/v1/invoices/$INVOICE_ID/pay \
  -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: success-example-1' -d '{"card_token":"tok_success"}'
```

The response is `202`, not financial success. GET the returned `status_url` with the same Authorization header to find the outcome. Repeating the accepted request/key returns the same initial response. Invoice GET shows its latest state.

### 4. Attempt a declined payment

Create a **different invoice** using example 2, then:

```sh
export DECLINE_INVOICE_ID='<new invoice id>'
curl -i http://localhost:8080/api/v1/invoices/$DECLINE_INVOICE_ID/pay \
  -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: decline-example-1' -d '{"card_token":"tok_card_declined"}'
```

Its accepted attempt eventually becomes `failed`; the invoice stays `open`. Other tokens: `tok_insufficient_funds`, `tok_timeout` (30 seconds), `tok_network_error`. An unknown outcome blocks another payment until reconciled. No refunds or partial payments exist.

## Tests and builds

With Java 21 and Docker available:

```sh
./mvnw test
./mvnw package
python3 scripts/test_receiver.py
```

Windows: use `mvnw.cmd test`. The wrapper downloads Maven 3.9.9. `PaymentIntegrationTest` needs Docker; the full test suite fails when Docker is absent. For explicitly limited local unit checks only, use `./mvnw -Dtest=InvoiceTotalTest,WebhookCryptoTest -Dsurefire.failIfNoSpecifiedTests=false test`. **A green build with skipped integration tests is not evidence of payment correctness.** Tests use real PostgreSQL and a controlled HTTP PSP fixture; they cover 20 competing clients, same-key replay, compressed timeout recovery, lost-success recovery, atomic rollback, and same-key races across different invoices. The separate Compose smoke test exercises the actual mock tokens, including its full 30-second delay:

```sh
docker compose --profile demo up -d --build
python3 scripts/smoke.py
```

The smoke script creates records in the local demo business. It does not delete them. `scripts/test_receiver.py` temporarily binds localhost:8090; stop the Compose demo receiver before running that standalone test.

Optional frontend build (Node 22):

```sh
cd frontend
npm ci
npm run build
```

CI in `.github/workflows/verify.yml` runs Java tests, frontend build, receiver checks, and Compose smoke on a Docker-capable runner. No CI execution is claimed merely because this workflow exists.

## API and architecture

- `openapi.yaml`: complete request/response contracts and errors.
- `DESIGN.md`: design decisions, state diagram, required failure cases, trade-offs.
- `AI_USAGE.md`: actual AI contribution, candidate choices, corrections and verification.
- `DECISION_LOG.md`: longer discussion history and subsequent refinements.
- `docs/MOCK_PSP.md`: extension contract required for durable recovery.
- `docs/VIDEO_GUIDE.md`: recording order, commands and code locations.
- `VERIFICATION.md`: executed checks and remaining gates.

Java was selected because the candidate has existing Java/Spring experience and can explain and maintain it; Rust was preferred by the brief but another language is permitted. The UI is an optional candidate-requested extension. For submission, it can be removed along with its Compose profile; the backend and curl demonstrations do not depend on it.

The API owns money math, payment eligibility and state transitions. Frontend code only handles presentation, form serialization, network requests and polling. It displays backend-formatted amounts.

## API key administration

An operator with local container access can create or revoke keys; there is no public signup or admin HTTP endpoint:

```sh
docker compose run --rm --no-deps app \
  --spring.profiles.active=key-admin --spring.main.web-application-type=none \
  --app.workers-enabled=false --app.demo-seed=false \
  --operation=create --business-id=11111111-1111-4111-8111-111111111111
```

Save the printed key once. Rotate by creating a second key, updating the client, then revoking the old key:

```sh
docker compose run --rm --no-deps app \
  --spring.profiles.active=key-admin --spring.main.web-application-type=none \
  --app.workers-enabled=false --app.demo-seed=false \
  --operation=revoke --business-id=11111111-1111-4111-8111-111111111111 \
  --key-id=<key uuid>
```

Existing in-flight authorized requests may complete after revocation; later authentication checks reject the key. Do not expose the local administrative environment publicly.

## Demo Video

**Pending candidate recording.** Replace this paragraph with your accessible 5–10 minute video link before submission. Architecture, live demo, unscripted state machine, and unscripted failure walkthrough must appear in that order. No video link has been fabricated.
