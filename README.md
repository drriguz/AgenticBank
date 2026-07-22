# AgenticBank

On-device AI ATM demo powered by **Gemma 4 E2B** running on **LiteRT-LM**. Users interact with a natural-language ATM chatbot on Android that performs banking operations against a Spring Boot backend.

## Features

- **Transfer** by card number
- **Deposit** / **Withdraw** with $10,000 per-transaction limit
- **Balance inquiry** / **Transaction history** with date filtering
- **Change password** via secure native dialog
- **Text-to-Speech** — auto-reads bot responses aloud
- Multimodal input: text, voice (hold-to-talk), image (extract card numbers from photos)
- On-device LLM inference — no cloud API calls for the chatbot

## Projects

| Directory | Stack | Build |
|-----------|-------|-------|
| [`android_app/`](android_app/) | Kotlin 2.2, Jetpack Compose, LiteRT-LM, minSdk 34 | `./gradlew assembleDebug` |
| [`verbvault/`](verbvault/) | Java 21, Spring Boot 4, SQLite, Maven | `./mvnw verify` |

## Quick Start

### 1. Start the backend

```bash
cd verbvault
./mvnw spring-boot:run
```

Seeds 11 test cards on first run (password `0000`, balance $5,000 each). Runs on `http://localhost:8080`.

### 2. Push the LLM model to device

```bash
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.riguz.agenticbank/files/
```

The model (~5 GB) is not bundled. The app searches:
1. App's external files dir (`/sdcard/Android/data/com.riguz.agenticbank/files/`)
2. `/sdcard/Download/`
3. MediaStore
4. Falls back to a file picker if auto-detect fails

### 3. Configure Android backend URL

Edit these files to change the backend IP:

- `android_app/app/src/main/java/com/riguz/agenticbank/agent/Tools.kt` — `BASE_URL` and `CARD_NUMBER`
- `android_app/app/src/main/java/com/riguz/agenticbank/agent/ToolExecutor.kt` — `API_BASE` and `DEFAULT_CARD_NUMBER`

Default: `http://192.168.31.66:8080`, card `4242424242424242` (John Smith).

### 4. Configure Text-to-Speech

The app uses Android's built-in TTS for reading bot responses aloud. On some devices:

1. Install **Google Text-to-Speech** from Play Store
2. Go to **Settings → Additional Settings → Accessibility → Text-to-speech output**
3. Set **Google Text-to-speech Engine** as preferred
4. Download **English (United States)** voice data

### 5. Build and install

```bash
cd android_app
./gradlew installDebug
```

## Test Cards

| Card Number | Cardholder | Type |
|-------------|------------|------|
| 4242 4242 4242 4242 | John Smith | Visa |
| 4000 0566 5556 5556 | Mary Johnson | Visa Debit |
| 5555 5555 5555 4444 | Robert Williams | Mastercard |
| 2223 0031 2200 3222 | Patricia Brown | Mastercard 2-series |
| 3782 8224 6310 005 | James Jones | Amex |
| 6011 1111 1111 1117 | Linda Garcia | Discover |
| 3530 1113 3300 0000 | Michael Miller | JCB |
| 6200 0000 0000 0005 | Elizabeth Davis | UnionPay |
| 4000 0000 0000 0002 | David Martinez | Visa (3D Secure) |
| 4000 0000 0000 0069 | Sarah Wilson | Visa (decline) |
| 5336 4100 8888 8888 | Test User | Mastercard |

Default password: `0000`. Initial balance: $5,000. John Smith also has seeded transaction history.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Android App                       │
│                                                     │
│  ┌─────────────┐   ┌─────────────┐   ┌───────────┐ │
│  │ ChatScreen  │──▶│  ToolExecutor│──▶│  Tools    │ │
│  │ (UI/TTS)    │   │  (dispatch)  │   │ (API call)│ │
│  └──────┬──────┘   └──────┬──────┘   └─────┬─────┘ │
│         │                 │                 │       │
│  ┌──────▼─────────────────────────────────────────┐ │
│  │              LiteRT-LM Engine                   │ │
│  │  Gemma 4 E2B (on-device, ~5GB, manual tools)  │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────┬───────────────────────────┘
                          │ REST API
┌─────────────────────────▼───────────────────────────┐
│              verbvault (Spring Boot)                 │
│  Controller → AtmService → JPA → SQLite             │
│  DataInitializer seeds 11 test cards on startup     │
└─────────────────────────────────────────────────────┘
```

## Tool Calling — How It Works

The LLM does **not** call tools automatically. The app uses **manual tool calling**:

1. **LLM generates tool call** — e.g., `call:withdraw{"amount":100}`
2. **App parses the tool call** — extracts tool name and parameters
3. **Validation** — `ToolExecutor.execute()` checks parameters (missing, out of range, etc.)
4. **User confirmation** — financial operations show an AlertDialog before executing
5. **API call** — `ToolExecutor.executeConfirmed()` calls the backend REST API
6. **Result fed back** — success/error response sent to LLM as tool response
7. **LLM generates final answer** — natural language response to the user

### Available Tools

| Tool | Parameters | Description |
|------|-----------|-------------|
| `get_current_date` | none | Returns today/yesterday dates (for relative date queries) |
| `check_balance` | none | Returns account name and balance |
| `transaction_history` | `period` (optional: "today", "yesterday", "last_7_days", "last_30_days") | Returns filtered transaction list |
| `deposit` | `amount` (number) | Deposits money (requires confirmation) |
| `withdraw` | `amount` (number) | Withdraws money (requires confirmation) |
| `transfer` | `amount` (number), `to_card_number` (string) | Transfers to another card (requires confirmation) |
| `change_password` | none | Shows secure password input dialog |

### Validation & Error Handling

- **Missing parameters** → returns `ValidationError` (hidden from UI, sent back to LLM to ask user)
- **Invalid values** → returns `ValidationError` with specific message
- **Backend errors** → returns `Error` (shown to user)
- **Malformed tool calls** — the Gemma 4B model sometimes generates invalid JSON. This is a model limitation, not a bug.

### Making the Model Correct

Several techniques help the small model generate correct tool calls:

1. **Explicit system prompt** — tells the LLM to use exact values, not guess, and to ask for missing info
2. **Simple tool schemas** — no `minimum`/`maxLength` constraints (these trigger the model to "fix" values)
3. **Keywords over dates** — `period="yesterday"` instead of ISO dates (the model can't reliably format `YYYY-MM-DD`)
4. **`parseAmount` handles both types** — the model sometimes generates numbers, sometimes strings
5. **Validation errors are hidden** — the LLM gets the error and asks the user, without showing technical details
6. **Card numbers are exact** — tool description says "use the EXACT value the user provided, do not modify or complete it"

## Backend API

### Accounts

| Method | Path | Body | Description |
|--------|------|------|-------------|
| `POST` | `/api/accounts` | `{"cardNumber":"...","name":"..."}` | Create account |
| `GET` | `/api/accounts/{cardNumber}` | — | Get balance |
| `GET` | `/api/accounts/{cardNumber}/transactions` | — | Transaction history (optional `?startDate=&endDate=`) |
| `PATCH` | `/api/accounts/{cardNumber}/password` | `{"cardNumber":"...","oldPassword":"...","newPassword":"..."}` | Change password |

### Transactions

| Method | Path | Body | Description |
|--------|------|------|-------------|
| `POST` | `/api/transactions/deposit` | `{"cardNumber":"...","amount":100}` | Deposit |
| `POST` | `/api/transactions/withdraw` | `{"cardNumber":"...","amount":100}` | Withdraw (max $10,000) |
| `POST` | `/api/transactions/transfer` | `{"fromCardNumber":"...","toCardNumber":"...","amount":100}` | Transfer |

### Error Codes

| Status | Meaning |
|--------|---------|
| `400` | Validation failure, insufficient balance, self-transfer, or ATM limit exceeded |
| `404` | Account not found |
| `409` | Optimistic locking conflict (concurrent update, retry) |

## Configuration

### Network Security

The app allows cleartext traffic to local development IPs:
- `10.0.2.2` (Android emulator localhost)
- `192.168.31.66` (local dev machine)
- `localhost`

Edit `android_app/app/src/main/res/xml/network_security_config.xml` to add your IP.

### Spring Profiles

| Profile | SQLite DB Location | Usage |
|---------|-------------------|-------|
| default | `./atm.db` (project root) | Local development |
| docker | `/atm-data/atm.db` | Docker container |
| test | `./atm-test.db` | Unit/integration tests |

### Docker

```bash
cd verbvault
./mvnw clean package -DskipTests
docker build -t atm .
docker run -d -p 8080:8080 -v atm-data:/atm-data atm
```

## License

Apache License 2.0
