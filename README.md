# Invoice and Payment Service

Create customers and invoices, try mock payments and see the resulting webhooks. The project includes a backend, PostgreSQL database, mock payment processor, webhook receiver and optional web interface.

## Demo Video

[Watch the demo video on Google Drive](https://drive.google.com/file/d/1Omd0nv8bCmK4SncS10A2koesBhMNKt8J/view?usp=sharing)

## Tech stack

These are the versions declared in the project files and Docker images. Image tags such as Java 21 and Node 22 do not pin a patch version.

| Component | Version | Used for |
|---|---|---|
| Java | 21 | Backend, mock processor and webhook receiver |
| Spring Boot | 3.5.7 | Java application framework |
| PostgreSQL | 18.6 Alpine | Database |
| Maven | 3.9.9 | Java builds and tests |
| Spring Data JPA and Hibernate | Managed by Spring Boot 3.5.7 | Database access |
| QueryDSL OpenFeign | 6.12 | Worker claims and queries |
| Liquibase | Managed by Spring Boot 3.5.7 | Database migrations |
| springdoc OpenAPI | 2.8.17 | API documentation |
| Testcontainers | 1.21.4 | PostgreSQL integration tests |
| React | 19.1.1 | Web interface |
| TypeScript | 5.9.3 | Frontend code |
| Vite | 6.4.1 | Frontend build |
| Node.js | 22 Alpine | Frontend build container |
| nginx | 1.27 Alpine | Serves the frontend and forwards API requests |
| Docker Compose | v2 required | Starts the complete application |

## Start the complete application

### 1. Prepare your computer

Install Docker with Compose v2. On macOS or Windows, open Docker Desktop and wait until its engine is running. On Linux, make sure the Docker daemon is running and your user can access it.

Extract the project ZIP. Open a terminal in the extracted folder that contains `docker-compose.yml`, `pom.xml` and this README.

Check that Docker is available

```sh
docker info
docker compose version
```

You do not need to install Java, Maven, Node or PostgreSQL on your computer. Docker builds and runs them. No manual database setup or `.env` file is needed for the demo.

### 2. Build and start everything including the frontend

```sh
docker compose --profile ui up -d --build --wait
```

Run this from the project folder. The first build needs an internet connection to download images and dependencies and can take several minutes.

The command starts PostgreSQL, applies the migrations and starts the backend, mock processor, webhook receiver and frontend. It creates a demo business, API key and webhook endpoint for you.

`-d` keeps the application running in the background. `--build` rebuilds the application images. `--wait` waits for services to be running or healthy according to their configured checks. The frontend has no separate health check, so also open it in your browser.

### 3. Check the application

```sh
docker compose --profile ui ps -a
```

The database, app, mock processor and receiver should be healthy. The frontend should be running. The `migrate` container should show `Exited (0)`. That is expected because its job ends when migrations finish.

Open http://localhost:3000 in your browser.

If startup fails, inspect the logs

```sh
docker compose --profile ui logs --tail=100
```

If a port is already in use, create a file named `.env` in the project folder with alternative host ports

```dotenv
APP_PORT=18080
RECEIVER_PORT=18090
UI_PORT=13000
DB_PORT=15432
```

Run the startup command again. With these values, the frontend is at http://localhost:13000 and the API is at http://localhost:18080/api/v1. Use the new ports in the examples below. Internal service addresses do not change. Keep the same project folder and `.env` for later commands.

## Use the application from the frontend

### 1. Connect

Open the frontend and find **Connect your workspace**. Paste this local demo API key and click **Connect**

```text
demo.local-demo-secret-change-for-real-use
```

The key is held in the current tab's memory. After refreshing or reopening the page, connect again. **Disconnect** clears the current connection. It does not delete records or stop the application.

### 2. Add a customer

1. Open **Customers**.
2. Click **Add customer**.
3. Enter a name such as `Demo Customer` and an email such as `customer@example.com`.
4. Click **Save customer**.

The customer appears in the list and becomes available when creating an invoice.

### 3. Create an invoice

1. Open **Invoices** and click **Create invoice**.
2. Select your customer and choose a due date.
3. Enter `Consulting` as the description, `3` as the quantity and `1299` as the unit price in cents.
4. Click **Add item**. Enter `Setup`, quantity `1` and unit price `5000`.
5. Click **Create invoice**.

Enter whole cents, not dollars. For example, `1299` means $12.99. The backend calculates this invoice's total as 8897 cents, displayed as $88.97. Its initial state is Open.

### 4. Try a successful payment

Open the invoice using its row's view button. Under **Test payment method**, select **Successful payment** and click **Pay**.

The page shows that payment has started and refreshes the result. Payment history should show Succeeded and the invoice should show Paid. The payment control is no longer available once the invoice is paid.

### 5. Try a failed payment

Create another invoice and open it. Select **Card declined** and click **Pay**.

Payment history should show Failed. The invoice stays Open. After confirmation of failure, **Start a new payment** lets you choose another method and make a new attempt.

**Send again** repeats the current request with the same idempotency key. It checks the same accepted request rather than creating a new payment attempt.

### 6. Try the other outcomes

Use a new invoice for each case so the results are easy to follow.

| UI option | What to expect |
|---|---|
| Successful payment | The attempt succeeds and the invoice becomes Paid |
| Card declined | The attempt fails and the invoice stays Open |
| Insufficient funds | The attempt fails and the invoice stays Open |
| Slow confirmation | The attempt becomes Unknown while the app checks the processor, then succeeds |
| Payment provider error | The attempt becomes Unknown, then fails when lookup confirms the processor result |

Slow confirmation uses the mock's real 30-second delay. Settlement takes about 41 seconds because the app checks on a retry schedule. Wait for the result. Unknown does not mean failed, and another charge remains blocked while the result is uncertain.

### 7. Check events and webhooks

Open **Events** to see invoice creation and payment outcome events. Open **Webhooks** to see delivery status, attempts and HTTP responses. Each invoice also shows its own webhook deliveries.

The demo receiver is already registered. You do not need **Add endpoint** for this walkthrough. Trying to register its active URL again returns a duplicate error.

You can also open http://localhost:8090/events to see events whose signatures the receiver verified. A delivered webhook normally has HTTP response 200.

## Stop, restart or delete the application data

Run these commands from the same project folder used at startup. Include the `ui` profile so the frontend is included too.

| What you want | Command | What it does |
|---|---|---|
| Stop and keep everything for later | `docker compose --profile ui stop` | Stops containers and keeps containers, images and data |
| Resume after stopping | `docker compose --profile ui start` | Starts the existing containers |
| Close the stack but keep your data | `docker compose --profile ui down` | Removes the stack's containers and network, but keeps database data and images |
| Start again after closing | `docker compose --profile ui up -d --build --wait` | Recreates containers and uses the saved data |
| Close and delete the saved data | `docker compose --profile ui down -v` | Removes containers, network and this project's named volumes |
| Remove saved data and project service images | `docker compose --profile ui down -v --rmi all` | Also removes service images where Docker allows it |

**Deleting volumes permanently removes customers, invoices, payment attempts, API keys, events and receiver history stored in this stack.** It also removes this project's Maven cache volume if present. It does not delete the extracted source folder. Images and build caches remain unless removed separately. Do not run a reset if you need the data.

To start again with fresh demo data after deleting volumes

```sh
docker compose --profile ui up -d --build --wait
```

Closing the browser or terminal does not stop containers started with `-d`.

## Service addresses

These addresses use the default ports.

| Service | Open or connect from your computer | Inside the Compose network |
|---|---|---|
| Frontend | http://localhost:3000 | `http://frontend:80` |
| Backend API | http://localhost:8080/api/v1 | `http://app:8080/api/v1` |
| Backend health | http://localhost:8080/actuator/health | `http://app:8080/actuator/health` |
| Swagger UI | http://localhost:8080/swagger-ui.html | Served by the app |
| OpenAPI document | http://localhost:8080/v3/api-docs.yaml | Served by the app |
| Verified receiver events | http://localhost:8090/events | `http://demo-receiver:8090/events` |
| Receiver webhook address | `http://localhost:8090/webhooks` accepts signed POST requests | `http://demo-receiver:8090/webhooks` |
| Mock processor | No host port is published | `http://mock-psp:8081` |
| PostgreSQL | `localhost:5432`, database `dodo` | `db:5432` |

For a local database viewer, the demo administrator is `postgres` with password `local_database_only`, unless you override `POSTGRES_PASSWORD` before database initialization. PostgreSQL is not a browser URL. Changing the environment variable later does not change the password in an existing database volume.

All published ports bind to localhost. Docker service names such as `app` and `mock-psp` resolve inside the Compose network, not in your computer's browser. The frontend forwards its API requests through nginx, so no separate API address needs to be entered in the UI.

For the backend-only setup required by the assignment, run `docker compose up`. The frontend is an optional addition and starts only with the `ui` profile.

## Four API examples

These commands use a POSIX shell such as Bash or zsh. Copy the returned IDs into the variables where shown. Choose new idempotency keys when repeating the demo with new invoices.

```sh
export API='http://localhost:8080/api/v1'
export AUTH='Authorization: Bearer demo.local-demo-secret-change-for-real-use'
```

### 1. Create a customer

```sh
curl -sS "$API/customers" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Demo Customer","email":"customer@example.com"}'
```

Expect HTTP 201. Copy the returned `id`.

### 2. Create an invoice

```sh
export CUSTOMER_ID='replace-with-customer-id'
curl -sS "$API/invoices" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"customer_id\":\"$CUSTOMER_ID\",\"due_date\":\"2026-11-15\",\"items\":[{\"description\":\"Consulting\",\"quantity\":3,\"unit_amount_cents\":1299},{\"description\":\"Setup\",\"quantity\":1,\"unit_amount_cents\":5000}]}"
```

Expect HTTP 201, state `open` and total `8897` cents. The backend calculates the total. A supplied total, decimal cents or numeric strings are rejected.

### 3. Make a successful payment

```sh
export INVOICE_ID='replace-with-invoice-id'
curl -sS -i "$API/invoices/$INVOICE_ID/pay" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-success-1' \
  -d '{"card_token":"tok_success"}'
```

Expect HTTP 202 with `payment_attempt_id`, `status_url` and a Location header. This means accepted, not paid. Copy the attempt ID and poll until it settles.

```sh
export ATTEMPT_ID='replace-with-payment-attempt-id'
curl -sS "$API/payment-attempts/$ATTEMPT_ID" -H "$AUTH"
curl -sS "$API/invoices/$INVOICE_ID" -H "$AUTH"
```

The attempt becomes `succeeded` and the invoice becomes `paid`. Repeating the same key and body returns the original 202 body. It does not return the latest status or call the processor again. A new key on the paid invoice returns HTTP 409.

### 4. Make a declined payment

Repeat step 2 to create another invoice. Do not use the paid invoice.

```sh
export DECLINE_INVOICE_ID='replace-with-new-invoice-id'
curl -sS -i "$API/invoices/$DECLINE_INVOICE_ID/pay" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-decline-1' \
  -d '{"card_token":"tok_card_declined"}'
```

Expect HTTP 202. Poll the returned attempt as above. It becomes `failed` with `failure_code` set to `card_declined`. The invoice stays `open` and can be retried using a new key.

| Token | Eventual result |
|---|---|
| `tok_success` | Succeeded |
| `tok_card_declined` | Failed with `card_declined` |
| `tok_insufficient_funds` | Failed with `insufficient_funds` |
| `tok_timeout` | Unknown after the worker times out, then succeeded through lookup |
| `tok_network_error` | Unknown after HTTP 500, then failed with `processor_error` through lookup |

The timeout token takes 30 seconds inside the mock. Settlement takes about 41 seconds because recovery uses scheduled lookups. This is not a response-time guarantee.

## Inspect webhooks through the API

```sh
curl -sS "$API/webhook-deliveries?invoice_id=$INVOICE_ID" -H "$AUTH"
curl -sS "$API/events" -H "$AUTH"
curl -sS 'http://localhost:8090/events'
```

The receiver is seeded automatically and receives `invoice.created`, `invoice.paid` and `invoice.payment_failed` events.

For your own receiver, send `POST /api/v1/webhook-endpoints` with a JSON `url` field containing a public HTTPS URL. Save the returned signing secret because it is shown only once. The signing and retry rules are in [DESIGN.md](DESIGN.md).

Lists are paginated. Use the returned `next_cursor` as the next request's `cursor` parameter until there are no more results. There is no manual webhook replay endpoint.

## Run the tests

The main suite uses real PostgreSQL containers and can run without a local JDK

```sh
docker compose --profile tests run --rm tests
```

This mounts the checkout and Docker socket. Testcontainers starts its own temporary database. The suite regenerates `openapi.yaml`. Missing Docker causes failure rather than silently skipping the database tests.

With Java 21 or later installed, the equivalent is

```sh
./mvnw -B clean test
```

On Windows use `mvnw.cmd -B clean test`.

The required payment scenarios are covered by these tests in `PaymentIntegrationTest`.

| Requirement | Test |
|---|---|
| Concurrent payment requests | `concurrentRequestsAcceptOneCharge` |
| Same-key replay without another POST | `sameKeyReplaysOriginalResponseWithoutSecondPspCall` |
| Timeout recovery | `timeoutIsUnknownThenRecoveredWithoutSecondCharge` |
| Processor HTTP 500 | `processorErrorStaysUnknownUntilLookupConfirmsFailure` |

Other tests cover rollback, lost success, stale workers, key conflicts, tenant isolation and webhook handling. No required scenario is intentionally omitted. The full Compose test is separate from the normal suite

```sh
docker compose up -d --build --wait
docker compose --profile e2e run --rm e2e
```

This checks all mock tokens, 20 concurrent callers, response replay, processor POST counts and verified webhooks. It adds demo records and does not delete them. It reads processor counts through the mock API, not through a direct SQL query.

The suite has 36 tests, and the end-to-end run passes.

## API documentation and errors

[openapi.yaml](openapi.yaml) is generated from the controllers and response types by `ApiDocumentTest`. Do not edit it by hand. Commit its updated output when the API changes. The workflow checks for a stale file.

Swagger UI is available at the address above. Use Authorize to enter the demo API key. The raw document is served at `/v3/api-docs.yaml`.

Errors have an `error` object containing `code`, `message` and `request_id`. Validation errors may include field details. Use the code in client logic and the request ID when reading logs. Examples include `payment_in_progress`, `invoice_already_paid` and `idempotency_key_conflict`.

## Project files

| File or folder | What it contains |
|---|---|
| [DESIGN.md](DESIGN.md) | Language choice, data model, state diagrams, failure cases and trade-offs |
| [AI_USAGE.md](AI_USAGE.md) | AI contribution, my decisions and corrections |
| [openapi.yaml](openapi.yaml) | Generated API specification |
| `app` | Identity, customers, billing, payments and notifications |
| `mock-psp` | HTTP mock payment processor |
| `webhook-receiver` | Signature-verifying Java receiver |
| `platform` | Shared infrastructure and tracing |
| `frontend` | React UI |
| `docker/init.sql` | Database roles, schemas and grants |
| `app/src/main/resources/db/changelog` | Liquibase migrations |

## Local demo settings

The demo business ID is `01a11810-ce4f-76f3-9863-cbf7566684b5`. Compose supplies public local database passwords, a demo API key, a webhook secret and an encryption key. These are for this demo only.

Create another key for the demo business

```sh
docker compose run --rm --no-deps app \
  --spring.profiles.active=key-admin --spring.main.web-application-type=none \
  --app.workers-enabled=false \
  --operation=create --business-id=01a11810-ce4f-76f3-9863-cbf7566684b5
```

The CLI prints the key once and gives its ID. To revoke it, use the same command with `--operation=revoke` and `--key-id=YOUR_KEY_ID`. Keep the business ID. Rotation means creating a second key, switching clients and revoking the old one.

This project is a local assignment demo. A real deployment needs a reviewed processor integration, settlement reconciliation, TLS, managed secrets, controlled webhook egress, rate limits and alerts. Changing `PSP_URL` alone does not make it compatible with a real provider.