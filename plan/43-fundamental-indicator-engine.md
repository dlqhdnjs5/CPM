## Understanding

Watchlist discovery currently has only a weak fundamental signal: it gives a small score when recent DART financial statement rows exist. To select companies that are financially sound, reasonably valued, and not merely active in news/price data, CPM needs a stored fundamental indicator layer.

The first implementation should use data CPM can collect now:

- OpenDART financial statements for revenue, operating income, net income, assets, liabilities, and equity.
- OpenDART stock total quantity API for issued/common share counts.
- Latest daily close price from `stock_price_daily`.

From those inputs CPM can calculate ROE, ROA, debt ratio, operating margin, net margin, growth rates, market cap, PER, PBR, and PSR.

## Implementation Plan

1. Add OpenDART stock quantity collection.
   - Add DTO/domain/mapper for `dart_stock_quantity`.
   - Add `DartFinancialClient.getStockTotalQuantity(corpCode, year, reportCode)`.
   - Add service method to fetch the latest annual stock quantity for a stock.

2. Add fundamental indicator calculation.
   - Add `stock_fundamental_indicator`.
   - Add pure calculation logic for profitability, stability, growth, and valuation ratios.
   - Persist one latest annual indicator row per stock/year/report.

3. Connect to watchlist discovery.
   - Candidate prefetch collects stock quantity and calculates fundamentals after DART financials.
   - Candidate metric SQL joins the latest fundamental indicator.
   - `WatchlistScoringEngine` uses real fundamental ratios instead of row count only.

4. Add admin APIs.
   - Fetch DART stock quantity for one stock.
   - Calculate fundamentals for one stock.
   - Query latest stored fundamentals for one stock.

5. Update docs and tests.
   - Update `AGENTS.md` and `.github/instructions/schema.instructions.md`.
   - Add unit tests for ratio/score calculation.
   - Add mapper rollback tests against the local MySQL test profile.
   - Run full Gradle test suite.

## Files / Changes

- New:
  - `src/main/java/com/bowon/cpm/dart/client/dto/DartStockQuantityResponse.java`
  - `src/main/java/com/bowon/cpm/dart/domain/DartStockQuantity.java`
  - `src/main/java/com/bowon/cpm/dart/mapper/DartStockQuantityMapper.java`
  - `src/main/resources/mapper/dart/DartStockQuantityMapper.xml`
  - `src/main/java/com/bowon/cpm/fundamental/domain/StockFundamentalIndicator.java`
  - `src/main/java/com/bowon/cpm/fundamental/mapper/StockFundamentalIndicatorMapper.java`
  - `src/main/resources/mapper/fundamental/StockFundamentalIndicatorMapper.xml`
  - `src/main/java/com/bowon/cpm/fundamental/service/FundamentalIndicatorCalculator.java`
  - `src/main/java/com/bowon/cpm/fundamental/service/FundamentalIndicatorService.java`
  - `src/main/java/com/bowon/cpm/admin/FundamentalController.java`

- Changed:
  - `DartFinancialClient`, `DartFinancialService`
  - `CandidatePrefetchService`, `StockDataBootstrapService`
  - `StockCandidateMetrics`, `StockCandidateScoreMapper.xml`, `WatchlistScoringEngine`
  - MyBatis mapper scan config
  - Schema docs

## DB Schema

```sql
CREATE TABLE IF NOT EXISTS dart_stock_quantity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    corp_code VARCHAR(20) NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    business_year INT NOT NULL,
    report_code VARCHAR(10) NOT NULL,
    stock_type VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    issued_stock_quantity BIGINT NULL,
    treasury_stock_quantity BIGINT NULL,
    distributed_stock_quantity BIGINT NULL,
    settlement_date DATE NULL,
    raw_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dart_stock_quantity_stock_report (stock_code, business_year, report_code, stock_type),
    KEY idx_dart_stock_quantity_corp_report (corp_code, business_year, report_code)
);

CREATE TABLE IF NOT EXISTS stock_fundamental_indicator (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    corp_code VARCHAR(20) NULL,
    business_year INT NOT NULL,
    report_code VARCHAR(10) NOT NULL,
    base_date DATE NULL,
    close_price DECIMAL(19,4) NULL,
    issued_shares BIGINT NULL,
    distributed_shares BIGINT NULL,
    market_cap DECIMAL(24,4) NULL,
    free_float_market_cap DECIMAL(24,4) NULL,
    revenue DECIMAL(24,4) NULL,
    operating_income DECIMAL(24,4) NULL,
    net_income DECIMAL(24,4) NULL,
    total_assets DECIMAL(24,4) NULL,
    total_liabilities DECIMAL(24,4) NULL,
    total_equity DECIMAL(24,4) NULL,
    per DECIMAL(19,6) NULL,
    pbr DECIMAL(19,6) NULL,
    psr DECIMAL(19,6) NULL,
    roe DECIMAL(19,6) NULL,
    roa DECIMAL(19,6) NULL,
    debt_ratio DECIMAL(19,6) NULL,
    operating_margin DECIMAL(19,6) NULL,
    net_margin DECIMAL(19,6) NULL,
    revenue_growth_rate DECIMAL(19,6) NULL,
    operating_income_growth_rate DECIMAL(19,6) NULL,
    net_income_growth_rate DECIMAL(19,6) NULL,
    profitability_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    stability_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    growth_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    valuation_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    total_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_fundamental_stock_report (stock_code, business_year, report_code),
    KEY idx_stock_fundamental_score (total_score, calculated_at),
    KEY idx_stock_fundamental_stock_date (stock_code, calculated_at)
);
```

## Test Steps

- `./gradlew.bat test --no-daemon`
- MyBatis tests must insert test financial/price/quantity rows and roll back DML.

## Risks / Assumptions

- PER/PBR/PSR quality depends on having a recent close price and stock quantity row.
- OpenDART account IDs are preferred over account names because account names vary by company and encoding.
- The first ratio implementation uses annual report data first (`11011`).
