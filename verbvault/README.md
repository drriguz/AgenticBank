# AI ATM Simulator

Natural-language-driven ATM backend. The AI/LLM interaction is handled by the frontend — this service is a plain REST API for banking operations.

## Tech Stack

| Layer | Choice |
|-------|--------|
| Runtime | JDK 21 LTS |
| Framework | Spring Boot 4.x LTS |
| Database | SQLite |
| ORM | Spring Data JPA |
| Build | Maven |
| Container | Docker (eclipse-temurin:21-jre-alpine) |

## Quick Start (Local)

```bash
# Build and run
./mvnw clean package -DskipTests
java -jar target/atm-0.1.0.jar
```

App starts on **http://localhost:8080**. The SQLite database file is created at `atm.db` in the project root. 11 test cards are seeded on first startup.

## API Endpoints

All responses use the wrapper: `{"success": true, "data": {...}, "error": null}`

### Account

| Method | Path | Body | Description |
|--------|------|------|-------------|
| `POST` | `/api/accounts` | `{"cardNumber": "4242424242424242", "name": "Alice"}` | Create an account |
| `GET` | `/api/accounts/{cardNumber}` | — | Get account with balance |
| `GET` | `/api/accounts/{cardNumber}/transactions` | — | Transaction history |
| `PATCH` | `/api/accounts/{cardNumber}/password` | `{"cardNumber": "...", "oldPassword": "0000", "newPassword": "1234"}` | Change password |

### Transactions

| Method | Path | Body | Description |
|--------|------|------|-------------|
| `POST` | `/api/transactions/deposit` | `{"cardNumber": "4242424242424242", "amount": 500.00}` | Deposit money |
| `POST` | `/api/transactions/withdraw` | `{"cardNumber": "4242424242424242", "amount": 100.00}` | Withdraw money (max $10,000) |
| `POST` | `/api/transactions/transfer` | `{"fromCardNumber": "...", "toCardNumber": "...", "amount": 200.00}` | Transfer between cards |

### Error Codes

| Status | Meaning |
|--------|---------|
| `400` | Insufficient balance, self-transfer, ATM cash limit exceeded, or validation failure |
| `404` | Account not found |
| `409` | Concurrent update conflict (retry) |

Import `postman-collection.json` for a ready-to-use request set.

## Docker

### Build

```bash
./mvnw clean package -DskipTests
docker build -t atm-simulator .
```

### Run

```bash
docker run -d \
  --name atm \
  -v /opt/atm-data:/atm-data \
  -p 8080:8080 \
  atm-simulator
```

### How it works

- Local development uses `application.properties` → db at `atm.db`
- Docker uses `application-docker.properties` → db at `/atm-data/atm.db` inside the container
- The profile is activated automatically via `SPRING_PROFILES_ACTIVE=docker` in the Dockerfile
- Mount `/opt/atm-data` (or any host directory) to `/atm-data` to persist the database across container restarts

### Verify

```bash
# Check balance (John Smith's card)
curl -s http://localhost:8080/api/accounts/4242424242424242 | python3 -m json.tool

# Deposit
curl -s -X POST http://localhost:8080/api/transactions/deposit \
  -H 'Content-Type: application/json' \
  -d '{"cardNumber":"4242424242424242","amount":1000}' | python3 -m json.tool
```

## Running Tests

```bash
./mvnw test
```

## Project Structure

```
src/main/java/com/example/atm/
├── AtmApplication.java
├── config/
│   └── DataInitializer.java        # Seeds 11 test cards on startup
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
│   ├── ApiResponse.java
│   ├── CreateAccountRequest.java
│   ├── DepositRequest.java
│   ├── ChangePasswordRequest.java
│   └── TransferRequest.java
└── exception/
    └── GlobalExceptionHandler.java
```
