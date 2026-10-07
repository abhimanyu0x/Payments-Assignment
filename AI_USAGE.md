# AI Usage

This is a factual draft for the candidate to review before submission. It does not claim the candidate personally wrote or executed AI-produced code.

## Tool and scope

ChatGPT read the assignment, extracted its evaluation criteria, discussed alternatives, drafted the database and API contracts, generated Java/Spring Boot code, SQL, Docker configuration, React UI, tests, OpenAPI, and documentation. It used official Spring, PostgreSQL, Docker, Microsoft architecture and Stripe documentation to check design concepts. It used command-line tools to compile, inspect and test available parts. Executed results and skipped checks are recorded in VERIFICATION.md.

## Candidate decisions independent of or against AI suggestions

1. **Java over Rust.** ChatGPT initially recommended Rust because the brief strongly preferred it. The candidate explicitly chose Java/Spring Boot because they already know Java and are new to Rust. This prioritizes ownership and explanation over language preference. The assignment permits another language with justification.
2. **Optional frontend.** ChatGPT recommended omitting it because the brief excludes a UI. The candidate requested a modern UI for easier use/testing while keeping all authoritative business rules in the backend. It is separately selectable and can be removed from the submission. This is a scope trade-off, not a claim that a UI earns evaluation points.
3. **Separate module schemas.** The candidate proposed separate schemas in one PostgreSQL instance. This makes ownership visible while retaining local transactions. ChatGPT explained that schemas are not automatic security boundaries and added distinct app/PSP roles. The SQL syntax was parsed; runtime role/transaction verification still requires the Docker tests.

## Decisions accepted after discussion

The candidate initially considered HTTP between modules and saga orchestration to simplify later scaling. After discussing local atomicity, independent failures and horizontal replication, they accepted internal Java interfaces with HTTP only across process boundaries. Async 202 acceptance, row locking, persisted work, leases, PSP operation lookup and webhook signing were AI proposals discussed with the candidate. Agreement to continue is not evidence of independent invention or executed verification.

## Corrections and refinements

- The early async proposal named a request fingerprint but omitted the recoverable mock card token needed after a restart. The persisted attempt now includes the mock token; fingerprints cannot reconstruct requests.
- An invoice lock alone does not serialize a business-scoped idempotency key reused across different invoices. A unique constraint plus rollback/fresh lookup handles that case.
- A provisional 35-second worker HTTP timeout became five seconds to explicitly exercise timeout/reconciliation while the mock still completes after 30 seconds.
- Code review found a demo signing-secret length mismatch and a webhook worker permit-release error. Both were corrected before the final build. Receiver tests verify signature/deduplication behaviour; worker runtime behaviour is not thereby proved.
- The PSP's durable idempotency and lookup are documented extensions. The design does not falsely claim the provided specification already guaranteed them.

## Verification and ownership

The candidate has not yet recorded the required video or personally reported running the final Docker setup. They should run the commands, inspect the key transaction/worker files, and revise this draft to reflect what they actually checked. Do not replace pending verification with a generic assertion that everything works. The unscripted walkthrough must be in the candidate's own words.
