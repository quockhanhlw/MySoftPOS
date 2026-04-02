# CRUD Matrix (Backend endpoint <-> UI screen)

Last updated: 2026-04-02

Status legend:
- `Done`: endpoint is implemented and connected from UI flow.
- `Bug`: endpoint/UI flow exists but has behavior issue (known or recently fixed, needs regression check).
- `Missing`: backend endpoint exists but no UI integration, or UI calls endpoint that backend does not expose.

## Merchants

| Method | Endpoint | UI screen / flow | Status | Notes |
|---|---|---|---|---|
| GET | `/api/merchants` | `PosAccountManagementActivity` list merchants | Done | Main source for merchant management screen. |
| POST | `/api/merchants` | `PosAccountManagementActivity` add merchant dialog | Done | Inline validation + reload implemented. |
| PUT | `/api/merchants/{id}` | `PosAccountManagementActivity` edit merchant dialog | Done | Save updates list after success. |
| DELETE | `/api/merchants/{id}` | `PosAccountManagementActivity` delete merchant action | Bug | Historical issue reported by user (delete fail/intermittent). Re-test E2E needed. |
| GET | `/api/merchants/{id}/accounts` | `PosAccountManagementActivity` merchant -> account list dialog | Done | Primary account source. |

## Pos Accounts

| Method | Endpoint | UI screen / flow | Status | Notes |
|---|---|---|---|---|
| GET | `/api/pos-accounts` | `TransactionManagementActivity` load admin user map | Done | Used to resolve account names and scope in admin transaction screen. |
| POST | `/api/pos-accounts` | `PosAccountManagementActivity` add account dialog | Done | Account create integrated. |
| PUT | `/api/pos-accounts/{id}` | `PosAccountManagementActivity` edit account profile + TID/IP/Port | Done | Single-call save flow to avoid partial-success state. |
| PUT | `/api/pos-accounts/{id}/connection` | No direct screen bound (legacy compatibility route) | Done | Kept for backward compatibility; main UI no longer depends on this route. |
| DELETE | `/api/pos-accounts/{id}` | `PosAccountManagementActivity` delete account | Done | Critical cleanup fix added: terminal mapping is cleared before account delete. |
| PUT | `/api/pos-accounts/{id}/reset-password` | `PosAccountManagementActivity` account row action (reset password) | Done | Admin can reset password directly from account list. |

## Branches

| Method | Endpoint | UI screen / flow | Status | Notes |
|---|---|---|---|---|
| GET | `/api/merchants/{merchantId}/branches` | `PosAccountManagementActivity` branch picker | Done | Used before opening branch account list. |
| POST | `/api/merchants/{merchantId}/branches` | No screen bound | Missing | Backend supports create branch; app has no branch CRUD UI. |
| PUT | `/api/merchants/{merchantId}/branches/{branchId}` | No screen bound | Missing | Backend supports update branch; app has no branch edit UI. |
| DELETE | `/api/merchants/{merchantId}/branches/{branchId}` | No screen bound | Missing | Backend now supports guarded delete (cannot delete MAIN/linked branch); UI action pending. |
| GET | `/api/merchants/{merchantId}/branches/{branchId}/accounts` | `PosAccountManagementActivity` branch -> accounts | Done | Used when selecting a branch. |

## Terminals

| Method | Endpoint | UI screen / flow | Status | Notes |
|---|---|---|---|---|
| GET | `/api/terminals` | `PosAccountManagementActivity` terminal mapping prefill/badges | Done | Used for mapping source and host prefill. |
| POST | `/api/terminals` | No direct screen bound | Missing | Creation happens indirectly through account connection flow, not direct terminal UI. |
| PUT | `/api/terminals/{id}` | No direct screen bound | Missing | Update happens indirectly through account connection flow. |
| DELETE | `/api/terminals/{id}` | No direct screen bound | Missing | Backend supports admin-scoped delete; UI action pending. |

## Transactions

| Method | Endpoint | UI screen / flow | Status | Notes |
|---|---|---|---|---|
| POST | `/api/transactions/sync` | Device sync (`TransactionSyncManager`, `SyncWorker`) | Done | Online sync from app local DB to backend. |
| GET | `/api/transactions` | `TransactionManagementActivity` admin transaction list | Done | Critical fix added: now scoped by authenticated admin id server-side. |
| GET | `/api/transactions/terminal/{code}` | No screen bound | Missing | API exists in client interface but not used by UI. |
| GET | `/api/transactions/pos-accounts/{posAccountId}` | No screen bound | Missing | API exists in client interface but not used by UI. |

## Test Suites / Test Cases

| Method | Endpoint | UI screen / flow | Status | Notes |
|---|---|---|---|---|
| GET | `/api/test-suites` | `TestSuiteSyncManager` pull suites | Done | Sync integration present. |
| GET | `/api/test-suites/{id}` | No direct screen verified | Missing | API defined but no direct UI call found in current scan. |
| POST | `/api/test-suites` | No direct screen verified | Missing | API exists in `ApiService`; no direct call in current scan. |
| PUT | `/api/test-suites/{id}` | No direct screen verified | Missing | API exists in `ApiService`; no direct call in current scan. |
| DELETE | `/api/test-suites/{id}` | No direct screen verified | Missing | API exists in `ApiService`; no direct call in current scan. |
| GET | `/api/test-suites/{suiteId}/cases` | `TestSuiteSyncManager` pull cases | Done | Sync integration present. |
| POST | `/api/test-suites/{suiteId}/cases` | No direct screen verified | Missing | API exists in `ApiService`; no direct call in current scan. |
| PUT | `/api/test-suites/cases/{caseId}` | No direct screen verified | Missing | API exists in `ApiService`; no direct call in current scan. |
| DELETE | `/api/test-suites/cases/{caseId}` | No direct screen verified | Missing | API exists in `ApiService`; no direct call in current scan. |
| POST | `/api/test-suites/sync` | `TestSuiteSyncManager` push suites/cases | Done | Sync integration present. |

## Critical/High fixes covered in this patch set

1. Transaction visibility is scoped by admin in backend read paths:
   - `GET /api/transactions`
   - `GET /api/transactions/terminal/{code}`
   - `GET /api/transactions/pos-accounts/{posAccountId}`
2. Deleting a pos account now clears terminal mapping (`terminals.pos_account_id`) before delete.
3. Pos account contract normalization: `PosAccountDto.phone` is aligned to account login (`username`) and `merchantPhone` is exposed separately for merchant contact display.

