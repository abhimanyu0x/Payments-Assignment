CREATE SCHEMA identity;
CREATE SCHEMA customers;
CREATE SCHEMA billing;
CREATE SCHEMA payments;
CREATE SCHEMA notifications;
CREATE SCHEMA mock_psp;
CREATE TABLE identity.businesses (id uuid PRIMARY KEY, name text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE identity.api_keys (id uuid PRIMARY KEY, business_id uuid NOT NULL REFERENCES identity.businesses(id), key_prefix text NOT NULL UNIQUE, secret_hash text NOT NULL, revoked_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE customers.customers (id uuid PRIMARY KEY, business_id uuid NOT NULL REFERENCES identity.businesses(id), name text NOT NULL, email text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(business_id,id));
CREATE INDEX customers_list ON customers.customers(business_id,created_at DESC,id DESC);
CREATE TABLE billing.invoices (
 id uuid PRIMARY KEY, business_id uuid NOT NULL REFERENCES identity.businesses(id), customer_id uuid NOT NULL,
 state text NOT NULL DEFAULT 'open' CHECK(state IN ('open','paid')), currency text NOT NULL DEFAULT 'USD' CHECK(currency='USD'),
 total_amount_cents bigint NOT NULL CHECK(total_amount_cents BETWEEN 1 AND 1000000000000), due_date date NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), paid_at timestamptz,
 FOREIGN KEY(business_id,customer_id) REFERENCES customers.customers(business_id,id), UNIQUE(business_id,id),
 CHECK((state='paid')=(paid_at IS NOT NULL)));
CREATE INDEX invoices_list ON billing.invoices(business_id,created_at DESC,id DESC);
CREATE INDEX invoices_state_list ON billing.invoices(business_id,state,created_at DESC,id DESC);
CREATE TABLE billing.invoice_items (id uuid PRIMARY KEY, invoice_id uuid NOT NULL REFERENCES billing.invoices(id), position integer NOT NULL, description text NOT NULL, quantity integer NOT NULL CHECK(quantity BETWEEN 1 AND 10000), unit_amount_cents bigint NOT NULL CHECK(unit_amount_cents BETWEEN 0 AND 1000000000000), UNIQUE(invoice_id,position));
CREATE TABLE payments.payment_attempts (
 id uuid PRIMARY KEY, business_id uuid NOT NULL, invoice_id uuid NOT NULL,
 idempotency_key text NOT NULL, request_fingerprint text NOT NULL, accepted_response text NOT NULL,
 mock_card_token text NOT NULL, amount_cents bigint NOT NULL CHECK(amount_cents>0), psp_operation_id uuid NOT NULL UNIQUE,
 status text NOT NULL CHECK(status IN ('pending','unknown','succeeded','failed')),
 psp_reference text, failure_code text, last_error_code text,
 review_required boolean NOT NULL DEFAULT false,
 next_attempt_at timestamptz NOT NULL DEFAULT now(), lease_expires_at timestamptz, claim_version bigint NOT NULL DEFAULT 0,
 reconciliation_round_count integer NOT NULL DEFAULT 0, processor_call_count integer NOT NULL DEFAULT 0,
 recovery_deadline_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
 UNIQUE(business_id,idempotency_key), UNIQUE(business_id,id),
 FOREIGN KEY(business_id,invoice_id) REFERENCES billing.invoices(business_id,id),
 CHECK((status IN ('succeeded','failed'))=(completed_at IS NOT NULL)),
 CHECK(NOT review_required OR status='unknown'));
CREATE UNIQUE INDEX one_unresolved_attempt ON payments.payment_attempts(invoice_id) WHERE status IN ('pending','unknown');
CREATE UNIQUE INDEX one_successful_attempt ON payments.payment_attempts(invoice_id) WHERE status='succeeded';
CREATE INDEX payment_history ON payments.payment_attempts(invoice_id,created_at DESC,id DESC);
CREATE INDEX payment_due ON payments.payment_attempts(next_attempt_at) WHERE status IN ('pending','unknown') AND NOT review_required;
CREATE TABLE notifications.webhook_endpoints (id uuid PRIMARY KEY, business_id uuid NOT NULL REFERENCES identity.businesses(id), url text NOT NULL, secret_ciphertext text NOT NULL, active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(business_id,id));
CREATE INDEX endpoints_business ON notifications.webhook_endpoints(business_id);
CREATE TABLE notifications.events (id uuid PRIMARY KEY, business_id uuid NOT NULL REFERENCES identity.businesses(id), event_type text NOT NULL, source_id uuid NOT NULL, payload text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(business_id,event_type,source_id), UNIQUE(business_id,id));
CREATE INDEX events_list ON notifications.events(business_id,created_at DESC,id DESC);
CREATE TABLE notifications.webhook_deliveries (
 id uuid PRIMARY KEY, business_id uuid NOT NULL, event_id uuid NOT NULL, endpoint_id uuid NOT NULL,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','delivered','exhausted')),
 attempt_count integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now(), lease_expires_at timestamptz, claim_version bigint NOT NULL DEFAULT 0,
 last_http_status integer, last_error_code text, created_at timestamptz NOT NULL DEFAULT now(), delivered_at timestamptz,
 delivery_deadline_at timestamptz NOT NULL DEFAULT now()+interval '1 hour',
 FOREIGN KEY(business_id,event_id) REFERENCES notifications.events(business_id,id), FOREIGN KEY(business_id,endpoint_id) REFERENCES notifications.webhook_endpoints(business_id,id), UNIQUE(event_id,endpoint_id));
CREATE INDEX delivery_due ON notifications.webhook_deliveries(next_attempt_at) WHERE status='pending';
CREATE INDEX delivery_business ON notifications.webhook_deliveries(business_id,created_at DESC,id DESC);
CREATE TABLE mock_psp.operations (
 operation_id uuid PRIMARY KEY, request_fingerprint text NOT NULL, amount_cents bigint NOT NULL CHECK(amount_cents>0), currency text NOT NULL CHECK(currency='USD'), card_token text NOT NULL,
 status text NOT NULL CHECK(status IN ('pending','succeeded','failed')), psp_ref uuid, failure_code text,
 complete_after timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz, post_count integer NOT NULL DEFAULT 1);
CREATE INDEX mock_due ON mock_psp.operations(complete_after) WHERE status='pending';
GRANT USAGE ON SCHEMA identity,customers,billing,payments,notifications TO app_user;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA identity,customers,billing,payments,notifications TO app_user;
GRANT USAGE ON SCHEMA mock_psp TO psp_user;
GRANT SELECT,INSERT,UPDATE ON ALL TABLES IN SCHEMA mock_psp TO psp_user;

CREATE VIEW billing.invoice_payment_availability AS SELECT i.id AS invoice_id,EXISTS(SELECT 1 FROM payments.payment_attempts p WHERE p.invoice_id=i.id AND p.status IN ('pending','unknown')) AS unresolved FROM billing.invoices i;
GRANT SELECT ON billing.invoice_payment_availability TO app_user;
