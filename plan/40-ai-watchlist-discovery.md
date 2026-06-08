# Plan 40: AI Watchlist Discovery

## Understanding

AI decisions currently run only for stocks already present in `stock_master`. The missing piece is a discovery loop that finds promising companies from a real stock universe, promotes selected companies to the watchlist, bootstraps their AI input data, and then lets the existing AI decision/order pipeline run.

The process must not ask AI to invent stock codes. AI should choose only from real candidates produced by deterministic screening.

## Implementation Plan

- Treat `stock_master.is_active` as listed/usable and `stock_master.is_watched` as current monitoring target.
- Change monitoring schedulers to use watched stocks only.
- Add `stock_candidate_score` to persist daily candidate scores and AI/fallback selection status.
- Build candidates from `dart_corp_code` and available market/news/DART metrics.
- Score candidates with a deterministic rule engine.
- Let AI select up to 5 stocks from the top 30 scored candidates.
- Fallback to deterministic top candidates if AI selection fails or returns no valid picks.
- Keep held stocks watched.
- Enforce:
  - max watched stocks: 20
  - max daily additions: 5
  - AI candidate review target: top 30
- Add manual Admin API and a morning scheduler.
- Optionally call `StockDataBootstrapService` for newly watched stocks.

## Files / Changes

- `stock_master` domain/mapper: add `isWatched`, `findAllWatched`, watch/unwatch helpers.
- New watchlist package:
  - domain: `StockCandidateMetrics`, `StockCandidateScore`
  - mapper: `StockCandidateScoreMapper`
  - service: `WatchlistDiscoveryService`
- New controller:
  - `WatchlistController`
- New scheduler:
  - `WatchlistDiscoveryScheduler`
- Docs:
  - `AGENTS.md`
  - `.github/instructions/schema.instructions.md`

## API

- `POST /api/admin/watchlist/discover`
  - query params: `bootstrap`, `newsAnalyzeLimit`
- `GET /api/admin/watchlist/candidates?limit=30`

## Schema

```sql
CREATE TABLE IF NOT EXISTS stock_candidate_score (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    corp_code VARCHAR(20) NULL,
    score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    liquidity_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    technical_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    news_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    dart_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    fundamental_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    risk_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    reason VARCHAR(1000) NULL,
    candidate_status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE',
    scored_date DATE NOT NULL,
    scored_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_candidate_score_stock_date (stock_code, scored_date),
    KEY idx_stock_candidate_score_date_score (scored_date, score),
    KEY idx_stock_candidate_score_status (candidate_status, scored_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

`stock_master.is_watched` is the operational monitoring flag. `is_active` means the stock is listed/usable; `is_watched` means the schedulers should collect data and create AI decisions for it.

Candidate statuses:

- `CANDIDATE`: deterministic scoring completed.
- `AI_SELECTED`: OpenAI selected the stock from the top scored candidates.
- `FALLBACK_SELECTED`: deterministic fallback selected the stock because AI was unavailable or returned too few valid picks.

## Test Steps

- Unit test candidate scoring.
- Unit test fallback selection respects max daily additions.
- Web test discovery API delegates to service.
- Run `./gradlew.bat test`.

## Risks / Assumptions

- Candidate quality depends on available market/news/DART data. If the database has almost no candidate data, fallback selection may be weak.
- Full market universe should come from `dart_corp_code` first; a KRX/KIS full listed-stock sync can improve this later.
- AI selection is advisory; deterministic fallback keeps the scheduler operational if OpenAI is unavailable.
