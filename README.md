# MySoftPOS

MySoftPOS is an Android SoftPOS application for card payment testing and transaction operations.
It supports NFC card reading, manual and magstripe test card flows, ISO 8583 message processing, and admin/merchant management screens.

## Overview

- Android app module: `app`
- Backend module (separate Spring Boot service): `mysoftpos-backend`
- Roles: `ADMIN` and `MERCHANT` (POS account)
- Offline-first behavior with local persistence and background sync

## Key Features

- Authentication: login/register and JWT-based session handling
- Payment flows: Purchase, Balance Inquiry, Void (with rule-based restrictions)
- Card channels: NFC, Manual Entry, and Magstripe test cards
- ISO 8583 engine for request/response build, parse, and logging controls
- Admin management:
  - Merchant / POS account / terminal mapping
  - Transaction Management with filter support
  - Test Suites / Test Cases management
- Localization: English and Vietnamese resources (`values`, `values-vi`)

## Tech Stack (App)

- Language: Java
- Android SDK: `minSdk 26`, `targetSdk 36`, `compileSdk 36`
- UI: AndroidX + Material + ViewBinding
- Local DB: Room (SQLite)
- Networking: Retrofit 2 + OkHttp
- Background jobs: WorkManager
- Security: EncryptedSharedPreferences (`androidx.security:security-crypto`)
- NFC: Android NFC APIs

## Project Structure

- `app/src/main/java/com/example/mysoftpos/ui`: Activities and UI flows
- `app/src/main/java/com/example/mysoftpos/data`: local/remote data access
- `app/src/main/java/com/example/mysoftpos/iso8583`: ISO 8583 processing
- `app/src/main/java/com/example/mysoftpos/testsuite`: test suite features
- `app/src/main/assets/pos_config.json`: default POS/ISO config values
- `docs/`: technical notes and project documentation

## Configuration Notes

- Default backend base URL is defined in `ApiClient` and persisted in encrypted preferences.
- ISO host (bank/switch) IP/port is configured per POS account/terminal mapping from backend.
- Currency behavior (for example VND/USD decimal rules) is controlled by app transaction logic + config.

## Build and Run (Android)

From project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

Install and run the generated debug APK from Android Studio or ADB.

## Backend Integration

This app is designed to work with `mysoftpos-backend` for:

- Authentication and account/role management
- Merchant, branch, terminal, and POS account CRUD
- Transaction record sync and admin-side reporting
- Test suite/case and card data synchronization

Backend setup details are in `mysoftpos-backend/README.md`.

## Known Limitations

- App UI no longer exposes a forgot-password screen/flow.
- Backend still keeps `/api/auth/forgot-password/**` public for compatibility and manual QA/testing.
- Result: forgot-password APIs are callable from Swagger/Postman, but not reachable from current Android screens.

## Localization

- English (default): `app/src/main/res/values/strings.xml`
- Vietnamese: `app/src/main/res/values-vi/strings.xml`

## Security Notes

- Token/session data is stored using EncryptedSharedPreferences.
- HTTP BODY logging is not enabled in production code paths.
- Do not use production PAN/PIN data in test environments.
