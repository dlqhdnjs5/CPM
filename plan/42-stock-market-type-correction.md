# Plan 42: Stock Market Type Correction

## Understanding

`stock_master.market_type` already exists, but `MarketDataService` currently writes `"KOSPI"` when saving KIS daily/current-price data. That can incorrectly mark KOSDAQ stocks as KOSPI.

DART corp-code data does not contain KOSPI/KOSDAQ classification, and the currently implemented KIS quote/daily-price clients do not expose market type. So the safe immediate fix is:

- stop writing guessed `KOSPI`
- preserve existing known `KOSPI`/`KOSDAQ`
- use `UNKNOWN` when the source cannot identify the market
- provide Admin APIs to correct market type for one or many stocks

## Implementation Plan

- Change `MarketDataService` to upsert `UNKNOWN` instead of hardcoded `KOSPI`.
- Keep mapper behavior where incoming `UNKNOWN` does not overwrite existing non-UNKNOWN market type.
- Add `StockMarketTypeService`.
- Add mapper method `updateMarketType(stockCode, marketType)`.
- Add Admin API:
  - `PATCH /api/admin/stocks/{stockCode}/market-type?marketType=KOSDAQ`
  - `PATCH /api/admin/stocks/market-types` with JSON map of stockCode to marketType.
- Validate allowed values: `KOSPI`, `KOSDAQ`, `UNKNOWN`.
- Add unit/controller tests and a MyBatis rollback test.

## Test Steps

- `MarketDataServiceTest` verifies market data collection does not hardcode KOSPI.
- `StockMarketTypeServiceTest` validates allowed values.
- `AdminStockControllerTest` verifies API delegation.
- `StockMasterMapperTest` verifies market type update and UNKNOWN preservation against real DB with rollback.
- Run `./gradlew.bat test --no-daemon`.

## Future Work

Add a true listed-stock master sync using a reliable KRX/KIS source. That sync should populate `stock_master.market_type` automatically for all listed stocks.
