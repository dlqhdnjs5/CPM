# Plan 29: PAPER Ledger Separation and Stock Name Fix

## Understanding

PAPER mode must be measured from virtual executions, not from KIS execution sync or real portfolio tables. The current PAPER executor writes virtual positions into `portfolio_position`, so later KIS account/position sync can overwrite or mix the data used for PAPER performance.

`stock_master.stock_name` is also being saved as the stock code because daily price sync upserts `stock_master` with `(stockCode, stockCode)`.

## Implementation Plan

- Add PAPER-only ledger tables:
  - `paper_account_balance`
  - `paper_portfolio_position`
  - `paper_portfolio_profit_loss`
- Add mapper/domain/service support for PAPER account, position, and daily profit/loss snapshots.
- Change `PaperOrderExecutor` so virtual BUY/SELL updates only PAPER tables.
- Change SELL risk/order calculation and target/stop monitoring to read PAPER positions while trading mode is `PAPER`.
- Change paper performance API so recent daily profit/loss comes from `paper_portfolio_profit_loss`.
- Keep `portfolio_realized_profit_loss` for execution-level realized P/L, still filtered by `PAPER-%` broker order number.
- Fix stock name saving by mapping KIS current-price `hts_kor_isnm` into `StockQuoteResult.stockName`.
- Use quote stock name when saving current price or daily prices; fall back to existing DB name and only then to stock code.

## Files / Changes

- New paper package:
  - `paper/domain/*`
  - `paper/mapper/*`
  - `paper/service/PaperPortfolioService.java`
- Mapper XML:
  - `mapper/paper/*`
- Existing flows:
  - `PaperOrderExecutor`
  - `OrderService`
  - `TargetStopMonitorScheduler`
  - `PerformanceQueryService`
  - `MyBatisConfig`
  - `StockQuoteResult`
  - `KisMapper`
  - `MarketDataService`
- Docs:
  - `AGENTS.md`
  - `.github/instructions/schema.instructions.md`

## Test Steps

- Unit test PAPER ledger buy/sell updates.
- Unit test paper performance reads `paper_portfolio_profit_loss`.
- Unit test KIS current price maps stock name.
- Unit test market daily save upserts stock name from quote.
- Run `./gradlew.bat test`.

## Risks / Assumptions

- PAPER starting cash is seeded from the latest KIS account balance at the time of the first PAPER order for the account.
- Realized P/L remains in `portfolio_realized_profit_loss`; the `broker_order_no LIKE 'PAPER-%'` filter separates PAPER realized results.
- This change intentionally prevents PAPER orders from mutating `portfolio_position` or `account_balance`.
