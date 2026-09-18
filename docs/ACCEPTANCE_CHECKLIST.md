# Acceptance checklist

Implementation and verification are recorded separately. A checked implementation box does not mean a physical-device or live-provider test has passed.

## Implemented

- [x] Firebase email registration/login, logout and persistent authentication
- [x] Profile editing
- [x] Groups and registered members
- [x] Shared expense creation/edit/deletion
- [x] Equal, exact, percentage and shares splits
- [x] Decimal balances and before/after debt simplification
- [x] Manual settlement and updated balances/activity
- [x] PayPal Sandbox order, browser approval, server capture verification, cancellation, pending recovery and verified idempotent webhook handling
- [x] Personal expenses and pending Room synchronization
- [x] CameraX capture, ML Kit OCR, editable review and total-receipt group split
- [x] Merchant rules and Gemini fallback categorization
- [x] Monthly/category budgets and alerts
- [x] Personal/group analytics and search/filter controls
- [x] Room caching and PostgreSQL schema/migrations
- [x] Authenticated live events, activity feed, persisted notifications and FCM device registration
- [x] Demo data, README, setup, architecture and interview guide

## Verified in this workspace

- [x] Backend compile and executable jar
- [x] 29 backend tests, including authenticated HTTP/live events and PayPal mock-provider capture verification
- [x] Final Android debug assembly, 10 unit tests and lint (0 errors; 59 warnings, 1 hint)
- [x] Packaged local backend startup and all seven migrations on an isolated H2 database
- [x] Packaged HTTP smoke: health, authenticated profile, seeded groups, PayPal return page and anonymous payment verification rejection
- [x] Seeded group, personal expenses, analytics and merchant mapping (integration tests / earlier demo verification)
- [x] Group and personal expense IDs survive backend restart (earlier demo verification)
- [x] Earlier foundation APK installed/launched on RMX3081

## Remaining external/device verification

- [ ] Install current APK and walk through all navigation on the phone (ADB currently lists no device)
- [ ] Room process-restart and migration verification on device
- [ ] Camera permission, capture and real receipt OCR on device
- [ ] Firebase real account registration, login and restart persistence
- [ ] Gemini real unknown-merchant request
- [ ] PayPal Sandbox checkout success/cancel/failure and immediate server confirmation
- [ ] Real signed provider webhook delivery through a reachable HTTPS URL
- [ ] FCM delivery and two-user real-time update demonstration
- [ ] PostgreSQL startup and restart persistence (Docker/PostgreSQL unavailable here)
- [ ] Final phone screenshots

Use SETUP.md for exact credential configuration and scripts/install-demo.ps1 for USB installation. No live-money behavior is supported. H2 and mocked gateway tests do not substitute for these remaining checks.

