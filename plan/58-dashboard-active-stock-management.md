## Understanding

The dashboard should let the user add active stocks manually, and active/related stock displays should prefer stock names over raw stock codes.

## Implementation Plan

1. Add an admin stock endpoint that upserts a `stock_master` row with `is_active = true`.
2. Include the active stock list in the dashboard summary response.
3. Add an Active Stocks section to the dashboard with a small add form.
4. Render stock labels as `stockName` first and `stockCode` as secondary metadata.
5. Update focused tests for the dashboard summary and static resource shell.

## Files / Changes

- `MarketController`
  - Add `POST /api/stocks/active` for manual active stock registration.
- `DashboardSummary`
  - Add `activeStocks`.
- `DashboardService`
  - Load active stocks once and expose both count and list.
- `dashboard.html`, `dashboard.js`, `dashboard.css`
  - Add Active Stocks UI and name-first labels.
- Tests
  - Update dashboard service/static tests and add controller coverage where useful.

## Test Steps

- Run dashboard and market controller related tests.
- Verify `POST /api/stocks/active` works locally.
- Reload dashboard and confirm Active Stocks renders names first.

## Risks / Assumptions

- No DB schema change is required.
- Stock name is required because the dashboard should not expose stock code as the primary label.
