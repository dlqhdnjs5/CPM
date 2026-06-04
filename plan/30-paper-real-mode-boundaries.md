# Plan 30: PAPER/REAL Mode Boundaries

## Understanding

PAPER and REAL execution are separated, but some read APIs and sync jobs still expose or run REAL/KIS data while the application is in PAPER mode. This can make PAPER validation misleading and can allow a PAPER risk check result to be reused after switching to REAL.

## Implementation Plan

- Add `trading_mode` to `risk_check_result` and write the current mode on every risk check.
- Require `OrderService` to use the latest risk result for the current mode.
- Add `mode=PAPER|REAL|ALL` filters to order request and execution query APIs.
- Add PAPER account/position query APIs under `/api/account`.
- Prevent manual and scheduled execution sync from calling KIS while `cpm.trading.mode=PAPER`.
- Keep REAL/KIS account sync APIs as REAL account views, but expose PAPER views explicitly.

## Files / Changes

- `risk_check_result` schema and docs
- `RiskCheckResult` domain and mapper XML
- `OrderRequestMapper`, `OrderExecutionMapper`, controllers
- `PaperPortfolioService`, `AccountController`
- `ExecutionSyncService`, `ExecutionSyncScheduler`
- Unit/web tests for mode filtering and PAPER sync skip

## Test Steps

- `POST /api/risk/checks/{id}` stores `trading_mode`.
- `POST /api/orders/requests/{id}` only accepts risk checks for current mode.
- `GET /api/orders/requests?mode=PAPER` returns PAPER requests only.
- `GET /api/orders/executions?mode=PAPER` returns PAPER executions only.
- `POST /api/orders/executions/sync` skips KIS in PAPER mode.
- Run `./gradlew.bat test`.

## Risks / Assumptions

- Existing `risk_check_result` rows will be backfilled as `PAPER` because the current default mode is PAPER.
- REAL account sync endpoints remain available because checking the actual KIS account is still useful during PAPER operation.
