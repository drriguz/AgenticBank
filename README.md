# AgenticBank

On-device AI ATM demo powered by Gemma 4 running on LiteRT-LM. Users interact with a natural-language ATM chatbot on Android that performs banking operations against a Spring Boot backend.

## Features

- **Transfer** by card number
- **Deposit** / **Withdraw** with $10,000 per-transaction limit
- **Balance inquiry** / **Transaction history**
- **Change password** via secure native dialog
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

### 3. Update Android backend URL

Edit `android_app/app/src/main/java/com/riguz/agenticbank/agent/Tools.kt` and `ToolExecutor.kt` — change `BASE_URL`/`API_BASE` to your machine's IP.

### 4. Build and install the app

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

Default password: `0000`. Initial balance: $5,000.

## Architecture

```
┌─────────────────────────────┐
│       Android App           │
│  ┌───────────────────────┐  │
│  │   ChatScreen (UI)     │  │
│  └────────┬──────────────┘  │
│           │                 │
│  ┌────────▼──────────────┐  │
│  │  ToolExecutor         │  │
│  │  (parse LLM output,   │  │
│  │   call REST API)      │  │
│  └────────┬──────────────┘  │
│           │                 │
│  ┌────────▼──────────────┐  │
│  │  LiteRT-LM (Gemma 4)  │  │
│  │  (on-device inference) │  │
│  └───────────────────────┘  │
└─────────────┬───────────────┘
              │ REST API
┌─────────────▼───────────────┐
│     verbvault (Spring Boot) │
│  Controller → Service → JPA │
│         SQLite DB           │
└─────────────────────────────┘
```

- **Tool calling is manual**: LLM emits JSON → app parses → executes against backend → feeds results back
- Financial operations require user confirmation via native AlertDialog
- Password changes use a secure native input dialog (never sent through the chat)
- System prompt restricts the chatbot to banking-only questions

## License

Apache License 2.0
