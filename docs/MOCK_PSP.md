# Mock PSP contract

The app uses only HTTP; it cannot read mock_psp tables. The mock has separate database credentials.

POST /payments accepts operation_id (UUID), amount_cents (positive integer), currency (USD) and card_token. GET /payments/{operationId} returns the current operation outcome or 404. Confirmed outcomes use status=succeeded with psp_ref, or status=failed with code. Pending uses status=pending. POST with an existing operation ID and different parameters returns 409. Same-operation retry preserves the stored result and reference. No retention expiry is implemented.

Required token behaviour:

| Token | POST behaviour | Recovery |
|---|---|---|
| tok_success | Success after roughly 100ms | GET returns same success |
| tok_insufficient_funds | Failure after roughly 100ms | GET returns insufficient_funds |
| tok_card_declined | Failure after roughly 100ms | GET returns card_declined |
| tok_timeout | Sleeps until persisted 30-second completion time, then success | Background completion and GET survive caller disconnection/restart |
| tok_network_error | HTTP 500 | Explicit extension: persists failed/processor_error before response; GET confirms it |

Operation IDs and amounts are persisted before waiting; no database transaction stays open while sleeping. A conditional pending-to-terminal update establishes the result once. psp_ref uses the operation UUID (the payment attempt's UUIDv7), which satisfies the required UUID shape. post_count is the diagnostic request count, also returned in every response so tests can verify a single POST over HTTP; one operation row and conditional terminal transition model one charge. This is a simulation, not a real card network.

Durable deduplication and GET are extensions chosen to make crash recovery demonstrable. Production behaviour must follow the real provider's idempotency retention and lookup guarantees. An actual 500 could occur before or after a charge; the invoice app treats it as unknown until lookup confirms an outcome. If lookup remains unavailable, review_required is set and a new charge stays blocked.
