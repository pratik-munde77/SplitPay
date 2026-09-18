# Architecture

## Android layers

Compose screens render StateFlow exposed by Hilt ViewModels. Repositories coordinate Retrofit and account-scoped Room storage. Navigation passes IDs rather than serialized domain objects. DataStore stores theme preference; Firebase owns persistent authentication. SavedStateHandle retains receipt and payment state across recreation.

Personal expenses use integer paise locally. Room tracks PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE and SYNCED. A mutex serializes synchronization, preserving pending rows while merging server results. Stable expense UUIDs make retries idempotent. The profile and group details (including expenses/splits) are cached as account-scoped Room JSON snapshots. Group refresh requests are serialized to avoid overwriting newer results with an older response. Group writes remain online. Failed synchronization leaves pending records intact and shows an error; there is no claim of background sync.

## Backend modules

A single Spring Boot process contains auth, user, group, expense, split, settlement, personal, budget, analytics, receipt, payment, activity and notification packages. Controllers validate DTOs; transactional services enforce authorization and invariants; JPA repositories persist normalized rows. Flyway owns schema evolution. Default storage is PostgreSQL; an explicitly enabled, loopback-only demo profile uses a local H2 file.

## Authentication

Firebase email/password login produces an ID token. OkHttp attaches it as Bearer authorization. Spring Security invokes Firebase Admin verification including revocation checks, derives identity from the verified UID and synchronizes the local profile. Group membership and ownership are checked server-side. Debug demo authentication requires both an explicit backend demo profile and a private random token; release Android disables it.

## Expenses and balances

Creating/editing an expense locks the group, verifies membership for payer and participants, validates the split, and stores expense plus splits in one transaction. Edits require the current optimistic version. All server monetary arithmetic uses BigDecimal. Equal/percentage/shares allocation distributes residual paise deterministically; exact amounts must sum exactly.

Balance = paid − allocated share + outgoing successful settlements − incoming successful settlements. Pending, failed or cancelled payments do not affect balances. Expense edits are blocked while payments are pending to keep the checkout amount stable.

## Debt simplification

Separate negative balances (debtors) and positive balances (creditors), sort/match largest amounts and transfer the smaller magnitude until settled. Suggestions preserve net balances and never rewrite original expenses. Greedy matching gives a practical small transfer set, not a guarantee of the globally minimum number of transfers.

## Receipt and classification

CameraX captures to a private cache file, or the user chooses an image. Bundled ML Kit recognizes text on device. Heuristics prioritize total/grand-total lines, parse common dates and propose a merchant. An editable review is mandatory. The user saves a personal receipt expense or selects a group/payer/participants/split for the total. Item-level allocation is not claimed.

Known merchant rules avoid unnecessary AI calls. Unknown merchants go through the backend Gemini proxy with a strict JSON schema. Merchant/category/confidence are validated; failures return Other. The user can override the category. The Gemini key stays on the server.

## Settlement and PayPal Sandbox

Manual settlement derives the payer from authentication, locks the group and validates positive amount against payable/receivable balance, including pending reservations. Stable request IDs prevent duplicate creation.

PayPal orders use server-owned quotes stored alongside the INR ledger amount. Sandbox checkout uses USD at an explicit demonstration rate, displayed before browser approval. OAuth credentials remain on the backend and its provider host is fixed to sandbox. Stable PayPal-Request-Id values identify create, capture and refund operations. Only the original payer can order, verify or cancel through authenticated endpoints.

The backend reads the order, validates the settlement custom ID, amount, currency and intent, captures APPROVED orders, and re-reads capture details. Only a matching COMPLETED capture changes balances and emits the completion activity. Pending captures retain their debt reservation. Completed captures with stale debt are refunded with a stable request ID. Verified approval/capture webhooks run the same reconciliation under the group lock, making repeated deliveries idempotent.

The browser return page is informational and cannot mark a payment successful. Android persists the order/quote in SavedStateHandle and can resume pending settlements from group history. Legacy Razorpay backend endpoints remain available for existing integrations; Android no longer contains the Razorpay SDK. See PAYPAL_SANDBOX.md for setup and refund/dispute limitations.

## Events and notifications

REST performs all commands. AFTER_COMMIT events invalidate authorized connected group clients over /api/live. Each WebSocket handshake uses the same bearer authentication and outgoing delivery checks current membership. Android reconnects with backoff and refreshes through REST; lifecycle stop closes its connection.

Activity logs are transactional. Notification rows persist after commit; FCM device tokens belong to the authenticated user. Data messages carry the intended user ID and Android checks the currently synchronized account before displaying them. Budget alerts use per-user/month/category/threshold keys for deduplication. Dispatch is best effort, with no durable transactional outbox yet; clients always have REST refresh and the activity feed.

## Scaling choices

Redis is omitted until measurements show value. Start with indexed PostgreSQL, connection pooling, pagination and batched analytics. For multiple app instances add a transactional outbox, distributed event delivery and cross-instance WebSocket routing. Extract bounded modules only when independent deployment or workload isolation justifies it.


