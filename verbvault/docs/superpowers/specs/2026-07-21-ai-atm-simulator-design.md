# AI ATM Simulator — Design Spec

> Hackathon demo | 2026-07-21

## Overview

Backend REST API for an ATM simulator. The AI/natural-language interaction is handled entirely by the frontend; the backend is a plain REST API that performs banking operations on accounts.

## Tech Stack

| Category | Choice |
|----------|--------|
| Language | JDK 21 LTS |
| Framework | Spring Boot 4.x LTS |
| Database | SQLite (`org.xerial:sqlite-jdbc`) |
| Dialect | `org.hibernate.orm:hibernate-community-dialects` |
| ORM | Spring Data JPA |
| Build | Maven |
| Container | Docker, base image `eclipse-temurin:21-jre-alpine` |

## Data Model

### Account

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT AUTO_INCREMENT` | PK |
| `name` | `VARCHAR(255)` | Display name, not unique |
| `balance` | `DECIMAL(19,2)` | Default 0.00 |
| `version` | `BIGINT` | Optimistic lock for concurrency control |

### Transaction

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT AUTO_INCREMENT` | PK |
| `type` | `VARCHAR(20)` | `DEPOSIT` / `WITHDRAW` / `TRANSFER` |
| `fromAccountId` | `BIGINT` | Nullable — null for DEPOSIT |
| `toAccountId` | `BIGINT` | Nullable — null for WITHDRAW |
| `amount` | `DECIMAL(19,2)` | Always positive |
| `timestamp` | `DATETIME` | Auto-set on creation |

**Semantics:**
- **Deposit**: `toAccountId` filled, `fromAccountId = null` (money enters the system)
- **Withdraw**: `fromAccountId` filled, `toAccountId = null` (money leaves the system)
- **Transfer**: both `fromAccountId` and `toAccountId` filled. `fromAccountId == toAccountId` is rejected.

## API Endpoints

All endpoints return a unified response wrapper:

```json
{"success": true, "data": {...}, "error": null}
```

### Account

| Method | Path | Body | Returns |
|--------|------|------|---------|
| `POST` | `/api/accounts` | `{"name": "Alice"}` | Created account (with id and balance) |
| `GET` | `/api/accounts/{id}` | — | Account with current balance |

### Transactions

| Method | Path | Body | Returns |
|--------|------|------|---------|
| `POST` | `/api/transactions/deposit` | `{"accountId": 1, "amount": 500.00}` | Updated account |
| `POST` | `/api/transactions/withdraw` | `{"accountId": 1, "amount": 100.00}` | Updated account |
| `POST` | `/api/transactions/transfer` | `{"fromAccountId": 1, "toAccountId": 2, "amount": 200.00}` | Both updated accounts |
| `GET` | `/api/accounts/{id}/transactions` | — | List of transactions for that account |

### Request Validation

All amount fields: `@NotNull @Positive`
All accountId fields: `@NotNull`

## Business Logic

All write operations run inside a single `@Transactional` method in `AtmService`:

| Operation | Logic |
|-----------|-------|
| **Deposit** | `account.balance += amount`, insert Transaction(type=DEPOSIT) |
| **Withdraw** | Check `balance >= amount` → `balance -= amount`, insert Transaction(type=WITHDRAW) |
| **Transfer** | Check `fromAccountId != toAccountId` → Check `fromAccount.balance >= amount` → deduct from fromAccount, add to toAccount, insert Transaction(type=TRANSFER) |

### Concurrency Control

`Account` uses JPA `@Version` (optimistic locking). Two concurrent writes to the same account will cause the later one to throw `OptimisticLockException`, which is handled as `409 Conflict`.

## Error Handling

| Scenario | HTTP Status | Message |
|----------|-------------|---------|
| Insufficient balance | `400 Bad Request` | "Insufficient balance" |
| Self-transfer | `400 Bad Request` | "Cannot transfer to the same account" |
| Account not found | `404 Not Found` | "Account not found: {id}" |
| Validation failure (negative amount, null fields) | `400 Bad Request` | Field-level validation message |
| Concurrent update conflict | `409 Conflict` | "Account was modified by another request, please retry" |

Handled via `@RestControllerAdvice` `GlobalExceptionHandler`.

## Project Structure

```
src/main/java/com/example/atm/
├── AtmApplication.java
├── controller/
│   ├── AccountController.java
│   └── TransactionController.java
├── entity/
│   ├── Account.java
│   └── Transaction.java
├── repository/
│   ├── AccountRepository.java
│   └── TransactionRepository.java
├── service/
│   └── AtmService.java
├── dto/
│   ├── CreateAccountRequest.java
│   ├── DepositRequest.java
│   ├── WithdrawRequest.java
│   ├── TransferRequest.java
│   └── ApiResponse.java
└── exception/
    └── GlobalExceptionHandler.java
```

## Testing

- **Unit tests**: `AtmService` with mocked repositories. Cover happy path + insufficient balance + account not found + self-transfer.
- **Integration test**: Single test class with SQLite in-memory, walks through full flow: create accounts → deposit → transfer → withdraw → check history → check balance.
- Controller layer is not tested separately (logic lives in Service, controllers are pass-through).

## Docker

SQLite is a file database — no separate database container needed. Mount a volume for the `.db` file for persistence.

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/atm-*.jar app.jar
VOLUME /data
ENTRYPOINT ["java", "-jar", "/app.jar"]
```
