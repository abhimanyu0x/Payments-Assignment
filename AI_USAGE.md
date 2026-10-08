# AI Usage

This is a factual draft for the candidate to review before submission. It does not claim the candidate personally wrote or executed AI-produced code.

## Tool and scope

ChatGPT read the assignment, extracted its evaluation criteria, discussed alternatives, drafted the database and API contracts, generated Java/Spring Boot code, SQL, Docker configuration, React UI, tests, OpenAPI, and documentation. It used official Spring, PostgreSQL, Docker, Microsoft architecture and Stripe documentation to check design concepts. It used command-line tools to compile, inspect and test available parts. Executed results and skipped checks are recorded in VERIFICATION.md.

Claude Code (Anthropic) was then used to verify and finish the implementation on a machine with Docker. It ran the PostgreSQL integration tests, the Compose stack and the end-to-end smoke script, probed tenant isolation and key rotation, and exercised the optional UI in a browser. It also fixed what those runs exposed and rewrote DESIGN.md, README.md and VERIFICATION.md to match observed behaviour. Every executed command and result is in VERIFICATION.md.

## Candidate decisions independent of or against AI suggestions

1. **Java over Rust.** ChatGPT initially recommended Rust because the brief strongly preferred it. The candidate explicitly chose Java/Spring Boot because they already know Java and are new to Rust. This prioritizes ownership and explanation over language preference. The assignment permits another language with justification.
2. **Optional frontend.** ChatGPT recommended omitting it because the brief excludes a UI. The candidate requested a modern UI for easier use/testing while keeping all authoritative business rules in the backend. It is separately selectable and can be removed from the submission. This is a scope trade-off, not a claim that a UI earns evaluation points.
3. **Separate module schemas.** The candidate proposed separate schemas in one PostgreSQL instance. This makes ownership visible while retaining local transactions. ChatGPT explained that schemas are not automatic security boundaries and added distinct app/PSP roles. Role separation and transactional rollback were later confirmed against real PostgreSQL (see VERIFICATION.md).

## Decisions accepted after discussion

The candidate initially considered HTTP between modules and saga orchestration to simplify later scaling. After discussing local atomicity, independent failures and horizontal replication, they accepted internal Java interfaces with HTTP only across process boundaries. Async 202 acceptance, row locking, persisted work, leases, PSP operation lookup and webhook signing were AI proposals discussed with the candidate. Agreement to continue is not evidence of independent invention or executed verification.

## Corrections and refinements

- The early async proposal named a request fingerprint but omitted the recoverable mock card token needed after a restart. The persisted attempt now includes the mock token; fingerprints cannot reconstruct requests.
- An invoice lock alone does not serialize a business-scoped idempotency key reused across different invoices. A unique constraint plus rollback/fresh lookup handles that case.
- A provisional 35-second worker HTTP timeout became five seconds to explicitly exercise timeout/reconciliation while the mock still completes after 30 seconds.
- Code review found a demo signing-secret length mismatch and a webhook worker permit-release error. Both were corrected before the final build. Receiver tests verify signature/deduplication behaviour; worker runtime behaviour is not thereby proved.
- The PSP's durable idempotency and lookup are documented extensions. The design does not falsely claim the provided specification already guaranteed them.

- Claude Code verification found real defects that compilation and unit tests had not shown:
  - a generated Spring Security password printed in logs
  - no webhook receiver in the default Compose stack, so the seeded endpoint's deliveries could only fail
  - missing readiness healthchecks
  - validation errors naming fields in Java camelCase instead of the API's snake_case
  - several UI bugs, including an invalid API key displayed as "Connected"

  At the author's request it then replaced the hand-written OpenAPI file with one generated from the code by springdoc. This meant introducing typed response records. A before/after comparison confirmed the JSON field names were unchanged.

  Also at the author's request, it removed all code comments and hardened the Spring Security configuration. Each protection is covered by an integration test, and the before/after behaviour is recorded in VERIFICATION.md.

  At the author's request it also renamed enum constants to ALL_CAPS, keeping lowercase API values, switched every generated ID to time-ordered UUIDv7, and documented where each secret and setting is stored. Its first enum change let `?state=OPEN` through via a Spring fallback; a test caught this and it was fixed. When the author asked whether everything was done, a re-audit found statuses still compared as plain strings in the payment finalization path; those were converted to enums too, and the original claim of completeness was corrected.

  At the author's request it then made the repository Java-only. It replaced the Python webhook receiver, receiver tests and smoke script with a Spring Boot receiver module, JUnit tests and an `e2e`-tagged end-to-end test. It also moved to PostgreSQL 18 with database-generated `uuidv7()` keys, and replaced Flyway with per-table Liquibase changelogs. The old local databases were recreated as the author asked.

  In the next round, at the author's request, it did four things:

  - converted the Liquibase changelogs to XML, with author `Abhimanyu` and millisecond timestamp IDs
  - showed webhook delivery results per invoice in the UI
  - rewrote every user-facing message into short plain English with no technical internals, defined in one place
  - added Docker profiles so the tests and the end-to-end test need no local JDK

  The author identified the message problem; Claude Code chose the wording. Its own browser check then caught a wrong "too long" message for empty input, and short IDs that looked identical with UUIDv7. Both were fixed.

  When the author asked for full Liquibase XML instead of SQL, Claude Code found that open-source Liquibase silently ignores `checkConstraint`. A new catalog test showed zero CHECK constraints. It offered the options, and the author chose short SQL changesets for CHECKs only. It also replaced the cross-schema view with a Java interface, and published the database on localhost for IntelliJ.

  At the author's request it then replaced all `JdbcTemplate` SQL with JPA entities and QueryDSL repositories in every module. It explained QueryDSL SQL versus JPA, and the author chose JPA. It removed two columns that JPA with database-generated IDs made redundant, and checked the generated locking SQL. All existing payment-correctness tests passed without weakening.

  When the author still saw IntelliJ's SQL warning, it removed all SQL elements from the changelogs. CHECK constraints moved into `modifySql`, verified by tests that save through repositories. At the author's request it also rewrote every integration test to work through controllers, services and repositories instead of SQL. Failure injection uses a Spring spy bean, and lease expiry uses configuration; all 31 tests pass.

  It also added integration tests for PSP 500 handling, stale workers, exhausted recovery and cross-tenant access. Moving the receiver into the default stack and adding healthchecks were Claude Code's choices, not the author's.

  In the next round, at the author's request, it reduced boilerplate with Spring Boot idioms. Repositories became Spring Data interfaces, with QueryDSL kept only for worker claims and pages. Entities moved to Lombok builders, configuration to validated properties records, and HTTP calls to `RestClient`. The tests now share one base class with a `@ServiceConnection` container. Changelogs were renamed to `create_table_<table>.xml`. A Mockito spy on a transaction-proxied service broke one test; Claude Code switched that guard to `Assert.state` instead of weakening the test. The OpenAPI document was compared byte-for-byte before and after to show the API did not change.

  In the next round the author asked for a full codebase review without code changes. Claude Code listed 47 items. When the author asked to fix them all and add Micrometer Tracing with UUIDv7 only, it built the tracing, a shared `platform` module and the review fixes. While doing so it found that its own review item 46 was wrong (the Testcontainers pin is needed) and that Spring's enum fallback defeated the planned converter, and corrected both instead of weakening the strict lowercase rule.

  In the next round the author shared the brief again and asked for a full review, UI included, aimed at going live. Claude Code's review tool caps one report at 10 findings, so after fixing those it kept reviewing and fixed six more, including two regressions its own fixes caused (a missing database flag reset and a proxy header that broke the UI). Each regression was caught by a test or a browser check, not assumed.

## Verification and ownership

The Docker, integration, smoke and browser checks have now been executed by Claude Code (see VERIFICATION.md), but the candidate has not yet recorded the required video or personally reported re-running them. They should run the commands, inspect the key transaction/worker files, and revise this draft to reflect what they actually checked. Do not replace pending verification with a generic assertion that everything works. The unscripted walkthrough must be in the candidate's own words.
