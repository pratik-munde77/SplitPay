# PayPal Sandbox setup

SplitPay uses PayPal Sandbox for its Android payment flow. All OAuth credentials, order creation, capture, refund and webhook verification run on the backend. Android receives only a checkout URL and the stored quote. The API host is fixed to `https://api-m.sandbox.paypal.com`; there is no live-payment switch.

## Configure your accounts

1. Sign in to the [PayPal Developer Dashboard](https://developer.paypal.com/dashboard/).
2. Under Apps & Credentials, select **Sandbox** and create/select a REST application associated with a **Business sandbox account**. Copy its Client ID and Secret into the backend configuration below. Your ordinary PayPal login password is not the API secret.
3. Under Testing Tools / Sandbox Accounts, create or select a separate **Personal sandbox account** as the buyer. Use that account's sandbox email and password in the browser checkout, not the seller account. Give it test funds and ensure the business sandbox account can receive USD. US sandbox buyer/business accounts are a straightforward USD demo setup.
4. Copy `backend/.env.example` to `backend/.env` if it does not already exist. Fill in these values locally; do not put the secret in Android, source control or chat:

```dotenv
PAYPAL_CLIENT_ID=your_sandbox_rest_app_client_id
PAYPAL_CLIENT_SECRET=your_sandbox_rest_app_secret
PAYPAL_WEBHOOK_ID=
PAYPAL_RETURN_URL=http://localhost:8080/api/paypal/return
PAYPAL_SANDBOX_INR_PER_USD=100
```

The demo launcher reads these values from `backend/.env` as plain, unquoted `KEY=value` lines. Restart it after changing configuration. With `bootRun`, explicitly export the variables into the Java process. Docker Compose passes them to its backend service.

## INR ledger and USD demonstration

PayPal's [supported payment currencies](https://developer.paypal.com/api/codes/currency) do not include INR. SplitPay therefore keeps the group's INR debt and stores a separate USD sandbox quote when the settlement is created. The configurable rate defaults to **INR 100 per USD for demonstration only**, not a market rate. For example, INR 100 debt produces USD 1.00 in sandbox funds. The app shows both values and the rate before opening PayPal. Amounts rounding to USD 0.00 are rejected. Changing the configuration does not change an existing quote.

The configured business sandbox account receives test funds. This flow demonstrates settlement verification and updates the INR group ledger; it does not send real money or pay the named group member's PayPal account. Marketplace payouts and live payments are outside this sandbox integration.

## Run and try checkout

```powershell
.\gradlew.bat -p backend test bootJar
powershell -ExecutionPolicy Bypass -File scripts/run-demo.ps1
```

In a second terminal:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb reverse tcp:8080 tcp:8080
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use `scripts/configure-demo.ps1` first if the local demo has not been configured. The demo backend stays on loopback. The `localhost` return URL works on a USB-connected phone with the reverse port configured. For an emulator, also use `adb reverse`, or configure a reachable HTTPS return URL on a non-demo deployment. A public deployment needs `PAYPAL_RETURN_URL=https://your-backend/api/paypal/return`.

1. Open the local demo, select a group and your outgoing debt, then choose **PayPal Sandbox**.
2. Tap **Prepare PayPal Sandbox checkout**. Review the INR debt, USD quote and demonstration rate.
3. Tap **Open PayPal Sandbox** and approve using the personal sandbox buyer account.
4. Switch back to SplitPay and tap **Check payment status**. The return page does not itself confirm or capture a payment. The authenticated app request verifies the stored order, captures an approved order, and checks the resulting capture.
5. Confirm a SUCCESS settlement, one activity entry and one balance update. Repeated status checks must not apply the payment twice.
6. Try cancellation and an unapproved checkout. Neither changes the balance. A capture pending at PayPal remains reserved until it completes or fails; cancellation cannot erase a completed or pending capture.

The group Settlements tab can resume a CREATED/PENDING PayPal checkout after leaving the screen or restarting the app. Orders without a persisted provider ID cannot be recreated more than five hours after settlement creation, to avoid reusing PayPal's expired default idempotency window. Cancel that checkout and start a new one.

## Optional webhooks for delayed completion

Local checkout confirmation works without webhooks. For a Firebase-authenticated HTTPS backend deployment, add a webhook to the same Sandbox REST app:

- URL: `https://your-backend/api/webhooks/paypal`
- Events: `CHECKOUT.ORDER.APPROVED`, `PAYMENT.CAPTURE.COMPLETED`
- Copy the resulting webhook **ID** into `PAYPAL_WEBHOOK_ID` and restart the backend.

A loopback URL cannot receive provider webhooks. Do not expose the private demo-token backend to the internet. The server verifies PayPal's transmission headers with its signature-verification endpoint, then fetches the order again. It does not trust the webhook's supplied amount or status. Group locking and unique capture IDs prevent duplicate ledger effects. Use actual sandbox transactions when validating webhooks; a simulator event does not establish that a real order was captured.

Later merchant-initiated refunds, chargebacks and disputes after a successful settlement are not synchronized into the ledger by this demo. The implemented refund path covers captured checkouts whose debt can no longer be applied before settlement completion.

## Troubleshooting

- Missing configuration message: use Sandbox REST app credentials in the backend process, then restart.
- HTTP 401 from PayPal: check that Client ID and Secret belong to the same Sandbox app.
- No completed payment: approve as the buyer, then check again. PayPal PENDING is not SUCCESS.
- Return page cannot load: verify `adb reverse` or your HTTPS return URL, then switch back to the app and check payment status.
- Timeout: check the existing payment; do not assume it failed. Stable create/capture/refund request IDs and provider reads support retries.
- A previously stored Razorpay settlement remains legacy backend data; the Android app now creates and resumes PayPal settlements. The legacy backend endpoints are retained for compatibility, but require their separate test credentials.

## Verification references

- [PayPal API requests and sandbox host](https://developer.paypal.com/api/rest/requests/)
- [Orders API create, approve and capture](https://developer.paypal.com/api/rest/integration/orders-api/api-use-cases/standard/)
- [Webhook signature verification](https://developer.paypal.com/api/rest/webhooks/rest/)

Automated tests mock the provider and verify application behavior. A successful build is not evidence of an actual Sandbox transaction. See IMPLEMENTATION_STATUS.md for the latest evidence.
