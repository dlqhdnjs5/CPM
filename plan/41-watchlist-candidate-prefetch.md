# Plan 41: Watchlist Candidate Prefetch

## Understanding

`WatchlistDiscoveryScheduler` currently scores candidates using data already present in DB. For stocks that are not in `stock_master` and were never bootstrapped, price, indicator, news, DART event, and financial data can be empty. That makes discovery weak for truly new stocks.

The fix is to add a candidate prefetch stage before scoring:

```text
candidate universe
→ prefetch top candidate data
→ metrics query
→ deterministic score
→ AI review top 30
→ watched=true for selected stocks
→ full bootstrap for selected stocks
```

## Implementation Plan

- Add `cpm.watchlist.prefetch-candidate-limit`, default `30`.
- Add `StockCandidateScoreMapper.findPrefetchTargets(limit)`.
- Add `CandidatePrefetchService`.
- Prefetch should be best-effort and must not abort the whole discovery if one stock fails.
- Prefetch steps per candidate:
  - upsert `stock_master` as `is_active=1`, `is_watched=0`
  - fetch daily prices
  - calculate daily indicators
  - collect recent news
  - fetch recent DART disclosures
  - classify major DART events
  - fetch recent financial statements
  - run pending news analysis once per batch
- Change `findCandidateMetrics` so scoring candidates require at least latest daily price data.
- Keep selected-stock full bootstrap after watchlist promotion.

## Tests

- Unit test `CandidatePrefetchService` calls best-effort steps and continues on failures.
- Unit test `WatchlistDiscoveryService` calls prefetch before scoring and still falls back when OpenAI fails.
- Add MyBatis DB rollback test for:
  - `findPrefetchTargets`
  - `findCandidateMetrics`
  - `stock_candidate_score` upsert/status update
- Run full `./gradlew.bat test --no-daemon`.

## Notes

- This does not fetch every listed company every day.
- It prefetches up to 30 candidates by default, then AI reviews the scored top 30.
- Later improvement: add KRX/KIS full listed-stock universe and sector/liquidity coarse filters before prefetch.
