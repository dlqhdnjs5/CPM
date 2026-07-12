## Understanding

`stock_price_daily` rows exist for BUY candidates, but `trading_value` is `NULL`. Risk liquidity check reads `AVG(trading_value)`, so the average becomes `NULL` and BUY decisions are blocked as "liquidity data missing".

Observed local DB:

- `005930`, `000660` have daily rows and volume values.
- `trading_value` is `NULL` for those rows.
- `broker_api_log.response_body` is not stored for daily price calls, so we cannot confirm from logs whether KIS omitted the field or the DTO field mapping is wrong.

Current ingestion path:

- `MarketDataService.fetchAndSaveDailyPrices`
- maps KIS daily output to `StockPriceDaily`
- currently sets:

```java
.tradingValue(parseBigDecimal(o.tradingValue()))
```

If `o.tradingValue()` is blank/null, `trading_value` is saved as `NULL`.

## Problem Judgment

This is primarily a data ingestion robustness issue, not an AI prompt issue and not a risk prompt issue.

The selected fix is:

```text
When saving daily prices:
1. Ignore KIS tradingValue.
2. Always calculate tradingValue = closePrice * volume.
3. Persist the calculated value into stock_price_daily.trading_value.
```

This keeps the risk logic strict while making the market data complete enough for liquidity checks.

## Implementation Plan

1. Add helper logic in `MarketDataService`.
   - Parse close price.
   - Parse volume.
   - Always calculate `close * volume` when close/volume are present.

2. Use that helper when building `StockPriceDaily`.

3. Add focused unit test.
   - KIS daily output has close and volume but null/blank trading value.
   - Verify inserted `StockPriceDaily.tradingValue` equals `closePrice * volume`.

4. Add/update integration test assertion.
   - Persisted row has non-null `trading_value`.

5. Optional one-time DML for existing data.
   - Backfill current rows where `trading_value IS NULL`.
   - Query:

```sql
UPDATE stock_price_daily
SET trading_value = close_price * volume
WHERE trading_value IS NULL
  AND close_price IS NOT NULL
  AND volume IS NOT NULL;
```

## Files / Changes

- `src/main/java/com/bowon/cpm/market/service/MarketDataService.java`
- `src/test/java/com/bowon/cpm/market/service/MarketDataServiceTest.java`
- `src/test/java/com/bowon/cpm/market/service/MarketDataServiceIntegrationTest.java`

## Test Steps

- Run:

```powershell
.\gradlew.bat test --tests "com.bowon.cpm.market.service.MarketDataServiceTest" --tests "com.bowon.cpm.market.service.MarketDataServiceIntegrationTest"
```

- After code fix and app restart, rerun daily price fetch for active stocks.
- Or apply the one-time DML backfill to unblock existing rows immediately.

## Risks / Assumptions

- `closePrice * volume` is a standard approximation of daily trading value.
- KIS-provided trading value is intentionally ignored for consistency.
- The one-time DML is data-changing DML, not DDL; it should only be applied after user approval.
