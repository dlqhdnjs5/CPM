## Understanding

The current DART bootstrap only collects DART data. AI decisions still report missing data because news, daily prices, and indicators are collected by separate APIs.
The user wants one stock-code based API that prepares enough data for AI decision generation and wants tests before using curl.

## Implementation Plan

Add a new API that accepts a stock code or DART corp code:

- `POST /api/stocks/{stockCode}/ai-data/bootstrap?from=yyyy-MM-dd&to=yyyy-MM-dd&keyword=...`

The API will:

1. Resolve stock code and corp code from `dart_corp_code`.
2. Upsert `stock_master`.
3. Fetch DART disclosures.
4. Classify major events.
5. Fetch financial statements.
6. Fetch Naver news using keyword or stock name.
7. Analyze pending news.
8. Fetch current quote.
9. Fetch daily prices.
10. Calculate daily indicators.

Each step records success/failure so one external/API/schema problem does not produce an unhandled 500.

## Files / Changes

- Add `StockDataBootstrapService`.
- Add endpoint to `MarketController`.
- Add service and controller tests.
- Keep existing APIs unchanged.

## Test Steps

- Run focused unit/web tests.
- Run `compileJava` and `processResources`.
- After app restart, call the new API with curl.

## Risks / Assumptions

- No DB schema changes in this change.
- Current DB is missing `stock_price_daily`; the new API will report daily-price/indicator steps as failed until that table is created.
- If OpenAI/Naver/KIS credentials or network fail, those steps will be reported without breaking the whole API.
