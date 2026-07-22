# AgenticBank

On-device AI ATM demo. Two independent projects in one repo — no shared build, no monorepo tooling.

## Projects

| Directory | Stack | Build |
|-----------|-------|-------|
| `android_app/` | Kotlin 2.2, Jetpack Compose, LiteRT-LM, minSdk 34 | `./gradlew assembleDebug` (from `android_app/`) |
| `verbvault/` | Java 21, Spring Boot 4, SQLite, Maven | `./mvnw verify` (from `verbvault/`) |

See `android_app/AGENTS.md` for detailed Android instructions.

## Backend (verbvault)

- Runs on port **8080**. SQLite DB at `./atm.db` (created on first run).
- `atm.db` is committed to the repo — intentional for demo convenience.
- Tests: `./mvnw test` (unit + integration, uses separate `atm-test.db`).
- Docker: `docker build -t atm . && docker run -p 8080:8080 -v atm-data:/atm-data atm`
- API design doc: `verbvault/docs/superpowers/specs/2026-07-21-ai-atm-simulator-design.md`
- **ATM cash limit**: $10,000 per withdrawal (enforced in `AtmService.withdraw()`).

## Test Cards (seeded on startup)

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
| 5336 4100 8888 8888 | Test User | Mastercard (5-series) |

All seeded with $5,000 balance, default password `0000`. Stored without spaces in DB.

## Critical: Hardcoded Values

The Android app has **hardcoded** connection values that must be changed per environment:

- `agent/Tools.kt` — `BASE_URL` and `CARD_NUMBER`
- `agent/ToolExecutor.kt` — `API_BASE` and `DEFAULT_CARD_NUMBER`

Default: `http://192.168.31.66:8080`, card `4242424242424242` (John Smith).

## Model Setup

The on-device LLM (`gemma-4-E2B-it.litertlm`, ~5 GB) is not bundled. Push via ADB:

```
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.riguz.agenticbank/files/
```

The app also searches `/sdcard/Download/` and MediaStore. If auto-detect fails, a file picker is offered.

## Architecture Summary

- **Android**: Single-module app. `ModelManager` (AndroidViewModel) owns LiteRT-LM engine. Navigation is a two-state enum: `Splash` (model loading) → `ATM` (chat). Tool calling is manual — LLM emits JSON tool calls, app parses and executes against the backend REST API, then feeds results back. Financial operations require user confirmation via AlertDialog. System prompt restricts the chatbot to banking-only questions.
- **Backend**: Standard Spring Boot layered architecture — Controller → Service → Repository (JPA). All external APIs use card numbers (not internal IDs). Optimistic locking via `@Version`. Self-transfers prohibited. `DataInitializer` seeds 10 test cards on first startup.
