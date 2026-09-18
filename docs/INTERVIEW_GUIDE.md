# SplitPay AI interview guide

**Opening:** “I built a Compose Android app and Spring Boot backend for shared and personal expenses. The interesting correctness problems are decimal splits, concurrent balances, retry-safe settlement and verified payment callbacks. Receipt recognition proposes data; the user always reviews it.”

| Question | Answer |
|---|---|
| Why PostgreSQL? | Financial relationships need foreign keys, transactions, locking and reliable decimal storage. |
| Why Room? | Cached expenses remain readable offline; personal edits can be queued durably and observed by Compose. |
| Why Redis? | It is intentionally absent until useful. A future balance cache must be invalidated after committed mutations and must never authorize payments. |
| Why WebSockets? | They tell active group screens to refetch after another member changes data. |
| Why REST plus WebSockets? | REST commands provide validation, transactions and clear retry semantics; sockets are best-effort update signals. |
| How are balances calculated? | Paid minus allocated share, adjusted by successful outgoing/incoming settlements. Net balances sum to zero. |
| How does simplification work? | Match debtors and creditors using their net balances, without mutating expenses. Greedy matching is practical, not globally optimal in every case. |
| Why BigDecimal? | Decimal amounts and explicit rounding avoid binary floating-point money errors. Android stores paise as Long. |
| How are leftover paise handled? | Deterministic residual allocation keeps the sum exactly equal to the original amount. |
| How does Firebase work with Spring? | Android sends an ID token; Admin SDK verifies it and Spring derives the UID before checking membership. |
| How is PayPal Sandbox verified? | Server OAuth, stored order/custom ID and USD quote checks, followed by a COMPLETED capture before ledger success. |
| Why backend secrets? | APK contents can be inspected. Only the approval URL and quote reach Android; OAuth secrets stay on the server. |
| How does OCR work? | CameraX image → bundled ML Kit text → heuristic fields → editable review. Recognition is not a trusted financial record. |
| Why Gemini fallback? | Merchant rules are fast, predictable and free of model latency. Unknown merchants use structured output with validation and safe fallback. |
| Is it fully offline-first? | Personal records support durable pending changes; groups support cached reads. Financial group writes require online authorization. |
| How do you prevent duplicate payments? | Stable request IDs, stored order/payment IDs, group locks, idempotent capture processing and checking existing SUCCESS state. |
| How do concurrent edits work? | Pessimistic group locks serialize financial mutations; expense optimistic versions reject stale edits. |
| Can a late capture overpay? | The server revalidates balances; an invalidated captured test payment is refunded rather than applied. |
| How would you scale? | First measure query load and add pagination/indexing/batching; then outbox, distributed events and caching where justified. |
| How would you extract microservices? | Separate module contracts first. Payments/notifications can be isolated, but use idempotent events and avoid distributed transactions casually. |
| What would you improve next? | Durable notification outbox, robust sync conflict UX, instrumented migration/device tests, broader pagination and operational metrics. |

## Demonstration order

1. Show private debug demo or real configured Firebase login; explain their distinction.
2. Open Goa Trip and explain paid versus share.
3. Create an expense using each split type; demonstrate invalid exact/percentage validation.
4. Show simplified debts, settle manually, and show updated activity/balances.
5. With test credentials, initiate PayPal Sandbox, cancel once, then succeed and explain server verification.
6. Add a personal expense, suggest category, set a budget and inspect analytics.
7. Capture a receipt, correct a field and save; show group receipt splitting.
8. Disconnect networking and demonstrate cached personal records; reconnect and sync.

## Be precise about evidence

Do not claim live Firebase, PayPal Sandbox, FCM, Gemini, camera or PostgreSQL verification until it has actually been performed. Mock provider tests establish server behavior, not provider availability. Consult IMPLEMENTATION_STATUS.md before describing the project as interview-ready.

