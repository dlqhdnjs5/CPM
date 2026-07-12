# Plan 53: Remove Watchlist Discovery

## Understanding

The automatic company discovery/watchlist flow is making the data collection and AI decision pipeline harder to operate. The project should return to a simpler model where active stocks are usable directly, without `stock_master.is_watched` or candidate scoring.

## Implementation Plan

1. Remove watchlist discovery API, scheduler, services, mapper, domain classes, and mapper XML.
2. Remove `isWatched` from the stock domain, service, mapper interface, and SQL mapper.
3. Change scheduled batch jobs that used watched stocks to use active stocks.
4. Keep risk liquidity thresholds, but move them away from `cpm.watchlist` naming.
5. Remove watchlist tests and update affected stock/scheduler/risk tests.
6. Compile and run focused tests.
7. Apply approved DDL to drop `stock_master.is_watched` and `stock_candidate_score`.

## Files / Changes

- Delete `com.bowon.cpm.watchlist.*`.
- Delete `WatchlistController` and `WatchlistDiscoveryScheduler`.
- Delete `mapper/watchlist/StockCandidateScoreMapper.xml`.
- Remove `WatchlistProperties`; add a small liquidity config for risk thresholds.
- Update `StockMasterMapper.xml` to no longer select/insert/update `is_watched`.
- Update schedulers/services to call `findAllActive`.

## Test Steps

- Run `compileJava`.
- Run affected tests around stock mapper, schedulers, risk service, and admin controllers.

## Risks / Assumptions

- Dropping `stock_candidate_score` permanently removes historical candidate scoring data.
- Dropping `stock_master.is_watched` means all active stocks may be considered by scheduled collectors unless a later manual selection mechanism is added.
- Liquidity thresholds remain because they protect order risk checks and are not inherently tied to automatic discovery.
