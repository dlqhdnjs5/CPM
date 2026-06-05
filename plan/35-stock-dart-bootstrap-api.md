## Understanding

The user wants a single API that accepts `stockCode`, inserts/updates `stock_master`, and then performs the existing DART collection steps:

- disclosures fetch
- major events classify/fetch
- financial statements fetch

`stock_master.market_type` is `NOT NULL`, but DART corp code data does not provide KOSPI/KOSDAQ market type.

## Implementation Plan

Add a DART bootstrap endpoint:

- `POST /api/dart/{stockCode}/bootstrap?from=yyyy-MM-dd&to=yyyy-MM-dd`
- Look up `dart_corp_code` by `stockCode`.
- Upsert `stock_master` using DART `corp_name` and `corp_code`.
- Use `UNKNOWN` for `market_type` when no existing value is available.
- Run disclosure fetch, event classification, and financial fetch in order.
- Return counts for each step.

## Files / Changes

- `StockService.java`
  - Add `upsertStockMasterFromDart(...)`.
- `StockMasterMapper.xml`
  - Preserve/update `corp_code` safely.
  - Avoid null market type overwrite.
- `DartController.java`
  - Add bootstrap endpoint.

## Test Steps

- Run `compileJava`.
- Restart app.
- Call `POST /api/dart/005930/bootstrap?from=2026-03-01&to=2026-06-05`.

## Risks / Assumptions

- No DB schema changes.
- If `dart_corp_code` has not been synced, the new API fails clearly and asks for corp-code sync first.
- Market type is set to `UNKNOWN` if not already known.
