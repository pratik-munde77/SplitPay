# Free hosted interview demo

Use Render Free (Spring Boot), Neon Free (PostgreSQL), Firebase Spark
(email/password and FCM), Gemini API free tier, and PayPal Sandbox.
This keeps the existing Android features; payment is simulated sandbox money.
No local server or Docker Desktop is needed after deployment.
Free quotas and sleeping services apply; this is an interview setup, not an uptime guarantee.

## 1. Prepare the repository

Create a private GitHub repository and upload this project preserving its directories.
This workspace was not a Git repository when these instructions were prepared.
Use Git with the existing .gitignore so local.properties, .env, Firebase keys,
google-services.json, build output and .local are excluded. Review staged files.
Rotate the previously populated PayPal secret before deploying; .env.example now
contains placeholders. Never upload credentials or paste them into chat.

The added backend/Dockerfile.render builds and tests Java 21 code directly from
source. Its Docker build context must be the repository root. The existing
backend/Dockerfile remains for the prebuilt local Compose workflow.

## 2. Create Neon Free PostgreSQL

Sign up at https://console.neon.tech and select Free. Create a project named
splitpay and choose a region near your Render region. Use its default database
or create one named splitpay. In Connect, select a direct/unpooled connection.
Save hostname, database name, role and password privately.

Build the JDBC URL using the actual hostname and database:

```text
jdbc:postgresql://YOUR-NEON-HOST:5432/YOUR-DATABASE?sslmode=require
```

Set username and password separately below. Do not paste a postgresql:// URL
with embedded credentials into DATABASE_URL. Flyway creates tables at startup;
there is no SQL import required for a new database. Existing local demo data
is not migrated automatically.

## 3. Configure Firebase Spark

At https://console.firebase.google.com select the same project as the Android app.
Keep the Spark plan; this setup needs neither Firestore nor Storage nor Functions.
Enable Authentication > Sign-in method > Email/Password.
Register Android package com.example.splitpay if needed and save the downloaded
configuration locally as app/google-services.json.
In Project settings > Service accounts generate a Firebase Admin private key.
Save it privately for the Render secret file. Note the project's Project ID.
Ensure Firebase Cloud Messaging API is enabled in Google Cloud APIs & Services.

## 4. Configure Gemini and PayPal

Create an API key at https://aistudio.google.com/api-keys using a project on the
Gemini free tier, without enabling paid billing. The configured model is
gemini-3.1-flash-lite. Free quotas can reject requests; the app then falls back
to Other with manual correction. Use fictional receipts for the interview.

At https://developer.paypal.com/dashboard create a Sandbox REST app associated
with a Business sandbox account. Save its Client ID and Secret. Create a separate
Personal sandbox buyer with test funds; US sandbox accounts simplify the USD demo.
Do not use live credentials. See PAYPAL_SANDBOX.md for the checkout behavior.

## 5. Create the Render Free service

At https://dashboard.render.com create New > Web Service and connect your repository.

| Setting | Value |
| --- | --- |
| Runtime | Docker |
| Root directory | Leave blank (repository root) |
| Dockerfile path | backend/Dockerfile.render |
| Docker build context | . (repository root) |
| Instance type | Free |
| Health check path | /api/health |

Do not create a Render database: its free database expires after 30 days.
Under Environment > Secret Files add firebase-service-account.json and paste the
Firebase Admin JSON there. Render mounts it at /etc/secrets/firebase-service-account.json.

Add the following environment variables (replace placeholders; do not include quotes):

```dotenv
DATABASE_URL=jdbc:postgresql://YOUR-NEON-HOST:5432/YOUR-DATABASE?sslmode=require
POSTGRES_USER=YOUR-NEON-ROLE
POSTGRES_PASSWORD=YOUR-NEON-PASSWORD
FIREBASE_PROJECT_ID=YOUR-FIREBASE-PROJECT-ID
GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-service-account.json
GEMINI_API_KEY=YOUR-FREE-TIER-KEY
GEMINI_MODEL=gemini-3.1-flash-lite
PAYPAL_CLIENT_ID=YOUR-SANDBOX-CLIENT-ID
PAYPAL_CLIENT_SECRET=YOUR-NEW-SANDBOX-SECRET
PAYPAL_SANDBOX_INR_PER_USD=100
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0
SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=30000
```

Do not enable SPRING_PROFILES_ACTIVE=demo or upload local demo tokens.
Render supplies PORT, which this backend already reads. Deploy and wait for the
build and startup logs. The container has a 256 MB Java heap for the small free
instance; actual memory usage and startup must be verified after deployment.

Once Render assigns your HTTPS URL, add:

```dotenv
PAYPAL_RETURN_URL=https://YOUR-SERVICE.onrender.com/api/paypal/return
```

In the same PayPal Sandbox REST app, add a webhook:

- URL: https://YOUR-SERVICE.onrender.com/api/webhooks/paypal
- Events: CHECKOUT.ORDER.APPROVED and PAYMENT.CAPTURE.COMPLETED

Add its ID as PAYPAL_WEBHOOK_ID in Render, then Save and deploy.
Open https://YOUR-SERVICE.onrender.com/api/health and expect {"status":"UP"}.
This proves the process responds, not that every external integration works.

## 6. Connect Android and install

In the existing local.properties preserve sdk.dir and update:

```properties
splitpay.apiBaseUrl=https://YOUR-SERVICE.onrender.com/
splitpay.demoToken=
```

The trailing slash is required; do not append /api. Keep app/google-services.json
on your machine. From the project root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use the full Android SDK platform-tools/adb.exe path if adb is not in PATH.
No adb reverse is needed. Sign up through normal Firebase registration; the
local seeded Pratik/Rahul accounts do not exist on this new hosted database.
Register at least two users, then create a group and add the other registered user.

## 7. Verify before the interview

- Log in, restart the app, and confirm the session persists.
- Create a group, expense, split and manual settlement. Confirm balances.
- Add personal spending and a budget; check analytics and persistence after reopening.
- Scan a fictional receipt and review its OCR. Test an unfamiliar merchant for AI
  classification; a known merchant rule alone does not prove Gemini is working.
- Make a PayPal Sandbox settlement, approve as the Personal buyer, return to
  SplitPay and tap Check payment status. Confirm SUCCESS and a single ledger update.
- Allow Android notification permission; use two accounts/devices to verify live
  changes and background push delivery. Confirm webhook delivery in PayPal.

Render Free sleeps after 15 idle minutes. Five minutes before presenting, open
/api/health, wait for UP, then open a group to verify the database connection.
A cold start can take a minute or longer. Keep demo traffic within free quotas;
do not use an artificial keep-alive service. The database lives in Neon, so Render
restarts do not erase it. Neon also sleeps and has free usage/storage limits.

Account setup, a live deployment, real credentials, an Android rebuild with the
assigned URL, and these device checks are still required. Local tests cannot
prove the hosted integrations work.

## Official references

- Render free limits: https://render.com/docs/free
- Render Docker: https://render.com/docs/docker
- Render secrets: https://render.com/docs/configure-environment-variables
- Neon Free: https://neon.com/blog/how-to-make-the-most-of-neons-free-plan
- Firebase pricing: https://firebase.google.com/pricing
- Gemini free tier: https://ai.google.dev/gemini-api/docs/pricing
- PayPal Sandbox: https://developer.paypal.com/tools/sandbox/
