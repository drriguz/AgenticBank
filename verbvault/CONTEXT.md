# CONTEXT

> Domain glossary for the AI ATM Simulator. No implementation details.

## Core Concepts

- **Account** — A holder of funds in the system. Has a name and a balance. Each person maps to exactly one account in this demo (no joint accounts, no multi-account customers).
- **Transaction** — A record of money movement. Every transaction has a type (DEPOSIT, WITHDRAW, TRANSFER), an amount, a direction (from/to account), and a timestamp.

## Design Decisions

- **Concurrency** — Optimistic locking via JPA `@Version`. Concurrent writes to the same account are rejected with `409 Conflict` instead of silently corrupting the balance.
- **Deposit/Withdraw counterparty** — The system itself is the implicit counterparty (no vault account). Deposits enter the system, withdrawals leave it. Represented by a null `fromAccountId` or `toAccountId` on the Transaction.
- **Self-transfer** — Prohibited. `fromAccountId == toAccountId` is rejected with `400 Bad Request`.
- **Balance snapshot** — Transaction does NOT store the post-transaction balance. The frontend combines current balance (from GET /accounts/{id}) with transaction history to derive running balances. Adding `balanceAfter` would complicate the Transfer case (two accounts, two balances) with little demo benefit.
