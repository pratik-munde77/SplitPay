# Implementation status - 17 September 2026

The Android project uses Kotlin/Compose, Hilt, Retrofit, Room and Firebase. Its independent Java 21 / Spring Boot backend provides groups, expenses, balances, settlements, receipt classification, budgets, analytics and notifications. No Git metadata is present in this workspace.

## PayPal Sandbox update

- Android now uses PayPal Sandbox browser checkout; Razorpay SDK and Android checkout callbacks were removed.
- Backend creates and captures orders using OAuth against a fixed Sandbox host. Provider secrets never reach Android.
- Flyway V7 adds a stored USD checkout amount, currency and demonstration conversion rate alongside the original INR debt.
- Users review the quote before opening PayPal, then return to the app to check payment status. Browser return parameters cannot confirm a payment.
- Capture reconciliation checks ownership, stored order, settlement custom ID, amount and currency. Only a completed capture updates the ledger.
- Stable create/capture/refund request IDs and group locks protect retries. Cancellation reconciles existing captures, pending captures retain their reservation, and invalidated captured debts are refunded.
- Verified PayPal approval/capture webhooks reuse the same reconciliation. Existing Razorpay backend routes remain for compatibility.
- Saved checkout state survives Android recreation; pending payments can be resumed from group history.
- Setup, Docker variables and the demo launcher now support PayPal. Local backend/.env was created with blank credential slots; no account secret was supplied.

## Latest verification

- Backend: **29 tests passed**, including 11 new PayPal configuration/HTTP integration tests; executable jar built.
- Android: **10 tests passed**, including 5 new checkout/recovery tests; debug APK built.
- Android lint: **0 errors, 59 warnings, 1 hint**. Warnings have not all been resolved.
- Packaged backend startup applied all seven migrations to an isolated H2 database.
- Packaged HTTP smoke checks passed: health, authenticated profile, seeded groups, informational PayPal return page, and anonymous payment verification rejection. The temporary smoke server was stopped after verification.
- ADB currently reports no connected device, so the final APK has not been installed or walked through on a phone.

Build commands:

```powershell
.\gradlew.bat -p backend test bootJar
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

SHA-256: `3EA3F2BD3CD90C0F1C107079C1FB7F694395B6DBA6052D93B9CF6343329483E4`

## External acceptance still required

Android Firebase configuration exists, but real registration/login, FCM, Gemini classification, physical-device camera/OCR and PostgreSQL acceptance were not established in this pass. Tests use H2 and mocked payment gateways; they do not prove a real provider transaction.

Add your Sandbox REST app Client ID and Secret locally, follow [PayPal setup](PAYPAL_SANDBOX.md), and complete one buyer approval/capture, cancellation and signed webhook delivery. A connected phone is needed for the final navigation and browser-return walkthrough. Later refunds/disputes after successful settlement are not synchronized by this demonstration. No real money is transferred and the sandbox merchant receives test funds, not the named group member.

The code and build checks are complete for the PayPal integration; full external-service and device acceptance remains open. See [acceptance checklist](ACCEPTANCE_CHECKLIST.md).
