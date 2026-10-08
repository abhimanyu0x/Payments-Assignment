# AI Usage

I used Claude Code during this assignment.

## Where AI helped

- Understanding the problem statement and evaluation criteria.
- Writing boilerplate and repeated code.
- Setting up Docker images and Docker Compose to start the application with one command.
- Debugging issues and writing tests for payments, concurrency and failure recovery.
- Understanding API key hashing, webhook signing and encryption of stored secrets.
- Setting up the GitHub Actions workflow.

AI also contributed to parts of the implementation and review. The choices below were mine.

## Decisions I made

### Java and Spring Boot

AI initially suggested Rust because the assignment preferred it. I chose Java and Spring Boot because I already know Java and wanted to use a language I could confidently explain and maintain.

### Separate database schemas

I proposed separate schemas for each module within one PostgreSQL instance. This makes ownership clear while keeping related changes within one database transaction.

### An optional frontend

AI suggested leaving out the UI because it was outside the assignment scope. I requested one to make testing and demonstrations easier. Financial calculations and payment decisions stay in the backend. The frontend is optional.

## What changed my approach

I initially considered HTTP calls between modules and a saga for billing and payments. After discussing transaction boundaries and failure cases, I chose a modular monolith with internal Java interfaces. HTTP remains at the processor and webhook boundaries. This avoids introducing network failures between modules that need to commit changes together.

## One correction during review

An early design included a request fingerprint but missed the request data needed for recovery after a restart. A hash cannot reconstruct the card token. The payment attempt now stores the mock token and amount so recovery can use the same operation.
