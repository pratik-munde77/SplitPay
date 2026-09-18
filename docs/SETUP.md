# Local setup and external accounts

## Phone demo without external credentials

1. Run `powershell -ExecutionPolicy Bypass -File scripts/configure-demo.ps1` from the repository root. It creates a random local demo token in ignored `local.properties` and sets the debug API URL to loopback.
2. Build the backend: `.\gradlew.bat -p backend test bootJar`.
3. Run `scripts/run-demo.ps1` in a terminal. The demo profile binds to PC loopback and uses a persistent H2 file under ignored `backend/.local/`. This is a convenience demo database, not verification of PostgreSQL behavior.
4. Connect the phone with USB debugging enabled and accept the PC authorization prompt.
5. Run `adb reverse tcp:8080 tcp:8080` using Android SDK `platform-tools/adb.exe`.
6. Build `.\gradlew.bat :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
7. Open SplitPay AI and choose **Explore local demo as Pratik**. Profile should load from the backend.

The demo token is included only in the debug APK. Release sets the token to an empty string and hides the demo button. The backend only accepts that token with its explicit `demo` profile and a configured token of at least 32 characters. Do not expose the demo server publicly.

## Firebase email/password authentication

1. Open https://console.firebase.google.com/ and create or select a project.
2. Project settings â†’ General â†’ Your apps â†’ Add app â†’ Android.
3. Register package **com.example.splitpay**. Download `google-services.json` to **app/google-services.json**. It is ignored by Git. The Android build applies Google Services when the file exists.
4. Build â†’ Authentication â†’ Get started â†’ Sign-in method â†’ Email/Password â†’ Enable â†’ Save.
5. Project settings â†’ Service accounts â†’ Firebase Admin SDK â†’ Generate new private key. Save it privately, for example **backend/.local/service-account.json**. Never paste its contents into source code or chat.
6. Set backend environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the absolute path of that file and `FIREBASE_PROJECT_ID` to Project settings â†’ General â†’ Project ID.
7. Configure PostgreSQL below and start the backend without the demo profile. Rebuild Android, choose Register, and verify Profile loads. Close/reopen the app to verify persistent login; log out and verify the login screen returns.

Google Sign-In requires additional OAuth/SHA configuration and is optional; email/password is the primary login flow.

## PostgreSQL

For hosted PostgreSQL (including Render), set `DATABASE_URL` in `backend/.env`
to `jdbc:postgresql://HOST:5432/DATABASE?sslmode=require`. Keep `POSTGRES_DB`
as the database name only and set `POSTGRES_USER` and `POSTGRES_PASSWORD`
separately. Set `GOOGLE_APPLICATION_CREDENTIALS` to the absolute local path of
the Firebase service-account file. After building the backend JAR, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-backend.ps1
```

This launcher loads `.env`, binds to loopback and uses PostgreSQL with normal
Firebase authentication. It does not enable the demo profile or require Docker.

1. Install/start Docker Desktop, or use an existing PostgreSQL installation.
2. Copy `backend/.env.example` to `backend/.env`. Set `POSTGRES_DB`, `POSTGRES_USER` and a nonempty `POSTGRES_PASSWORD`.
3. Run `docker compose --env-file backend/.env -f backend/compose.yml up -d`.
4. In the backend process environment set `DATABASE_URL=jdbc:postgresql://localhost:5432/splitpay`, `POSTGRES_USER` and `POSTGRES_PASSWORD` to the same values.
5. Run `.\gradlew.bat -p backend bootRun` without `SPRING_PROFILES_ACTIVE=demo`. Flyway creates/version-controls the schema and Hibernate validates it.
6. Register/login from the app; verify a `users` row exists and survives a backend restart. `/api/health` is only process health and does not independently prove all integrations work.

Docker `.env` variables are not automatically imported into a separately launched Java process; set them in that process environment explicitly.

## Gemini classification

1. Open https://aistudio.google.com/api-keys.
2. Select/create the Google Cloud project and create an API key.
3. Store the value as backend environment variable `GEMINI_API_KEY`, never in Android properties.
4. The classification endpoint uses known merchant mappings first; an unknown merchant should invoke the configured model. A failed request must return Other and permit manual selection.

## PayPal Sandbox

Follow [the PayPal Sandbox guide](PAYPAL_SANDBOX.md) to configure your Business REST app, Personal buyer account, USD test quote, return URL and optional verified webhooks. Copy the PayPal variables from backend/.env.example into your local backend/.env, then restart the backend. Secrets stay on the server.

## Firebase Cloud Messaging

The same Android Firebase configuration and backend service account are used. In Google Cloud Console â†’ APIs & Services, ensure Firebase Cloud Messaging API is enabled for the project. On Android 13+, grant notification permission. Device-token registration and server push must be tested on the connected phone after Firebase configuration is supplied.

## Credential checks

These instructions describe configuration, not a claim that credentials have been supplied or all phases are complete. See `IMPLEMENTATION_STATUS.md` for verified behavior.

The demo launcher reads Gemini, PayPal and Firebase values from backend/.env. Use plain KEY=value lines without shell expressions or quotes. Use an absolute host path for GOOGLE_APPLICATION_CREDENTIALS when running Java directly. Restart the backend after changing values.

For the full Docker backend, build bootJar, place the service account at backend/.local/service-account.json, then run docker compose --env-file backend/.env -f backend/compose.yml --profile app up --build -d. The app container uses PostgreSQL and Firebase; it does not enable demo authentication.

