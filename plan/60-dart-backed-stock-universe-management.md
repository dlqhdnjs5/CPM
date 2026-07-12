## Understanding

The dashboard stock universe flow should not allow arbitrary stock code/name pairs. Search should use listed companies from `dart_corp_code`, exclude already-active stocks, and adding a result should activate it and run AI-data bootstrap. Existing `stock_master` rows should be visible with a toggle that updates `is_active`.

## Implementation Plan

1. Add mapper queries:
   - Search inactive/listed DART candidates.
   - List all `stock_master` rows.
   - Update `stock_master.is_active`.
2. Replace the manual active stock API with DART-backed activation:
   - `GET /api/stocks/candidates?query=...`
   - `POST /api/stocks/active` with `stockCode`, then run bootstrap.
   - `PATCH /api/stocks/{stockCode}/active` to toggle active state.
3. Extend dashboard summary with all stock-master rows for toggle rendering.
4. Replace the dashboard form with one autocomplete-style search input and candidate result buttons.
5. Render `stock_master` list with toggles.
6. Update focused controller/dashboard tests and run them.

## Files / Changes

- `DartCorpCodeMapper`, `DartCorpCodeMapper.xml`
- `StockMasterMapper`, `StockMasterMapper.xml`
- `MarketController`
- `DashboardSummary`, `DashboardService`
- `dashboard.html`, `dashboard.js`, `dashboard.css`
- Focused tests

## Test Steps

- Run MarketController and dashboard-related tests.

## Risks / Assumptions

- Bootstrap can call external APIs and may partially fail by design; the result exposes step success/failure.
- Toggle only updates `is_active`; it does not run bootstrap unless the user adds from DART search.
