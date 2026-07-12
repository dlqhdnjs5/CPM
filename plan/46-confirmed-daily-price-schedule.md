## Understanding

`stock_price_daily` is consumed as a confirmed daily candle source by AI prompts, technical indicators, watchlist scoring, and fundamental price-based calculations. It must not contain intraday or pre-market temporary candles. Changing only one scheduler time is insufficient because manual bootstrap and watchlist prefetch also call the same daily-price fetch service.

## Implementation Plan

1. Treat `stock_price_daily` as confirmed daily OHLCV only.
2. Filter KIS daily-price rows at the service boundary:
   - before market close, skip rows whose trade date is today or later.
   - after market close, allow today's row.
3. Move `DailyPriceSyncScheduler` from before-market to after close, after the market should have final daily candles.
4. Keep the existing upsert behavior so corrected final candles replace stale data.
5. Add integration-style tests with real MyBatis mapper and test DB transaction rollback:
   - before close: KIS response includes today + yesterday, only yesterday is persisted.
   - after close: today's row is persisted.
   - duplicate date is updated by upsert.

## Files / Changes

- `MarketDataService.java`
- `DailyPriceSyncScheduler.java`
- `MarketDataServiceIntegrationTest.java`

## Test Steps

- Run `MarketDataServiceIntegrationTest`.
- Run prompt builder tests to ensure AI prompt defense still passes.

## Risks / Assumptions

- Korean market close is treated as 15:30 local time.
- KIS may still return today's partial row before close; this implementation filters it regardless of call path.
- Existing bad rows are not deleted automatically, but future fetches can update them after close.
