---
applyTo: '**'
description: 'DB schema reference and schema-change rules'
---

# CPM DB Schema Instructions

## Connection

- Host: localhost:3306
- Database: cpm
- Username: set via `DB_USERNAME` or `application-local.yml`
- Password: set via `DB_PASSWORD` or `application-local.yml`
- Charset: utf8mb4 (utf8mb4_0900_ai_ci)

## Rules

1. Schema changes are part of the core project contract.
2. When code adds or changes a table/column/index, update this document and `AGENTS.md`.
3. MyBatis domain fields use Java camelCase with `map-underscore-to-camel-case=true`.
4. Do not log secrets, API keys, or full account numbers.

## Domain Tables

### Stock / Market

- `stock_master` (PK: stock_code) - stock master data.
  - `is_active`: listed/usable stock flag.
  - `is_watched`: current monitoring target flag used by schedulers and AI decision generation.
- `stock_candidate_score` (PK: id, UK: stock_code+scored_date) - daily watchlist discovery candidate score and AI/fallback selection status.
  - Discovery prefetch fills candidate market/news/DART data before scoring.
- `stock_fundamental_indicator` (PK: id, UK: stock_code+business_year+report_code) - calculated ROE/ROA/debt/margin/growth/PER/PBR/PSR and fundamental score.
- `stock_price_daily` (PK: id, UK: stock_code+trade_date)
- `stock_price_minute` (PK: id, UK: stock_code+trade_datetime+interval_minute)
- `stock_realtime_quote` (PK: id)
- `stock_orderbook` (PK: id)
- `stock_indicator_daily` (PK: id, UK: stock_code+trade_date)
- `stock_indicator_minute` (PK: id, UK: stock_code+trade_datetime+interval_minute)
- `market_index` (PK: id, UK: index_code+trade_date)

### OpenDART

- `dart_corp_code` (PK: corp_code)
- `dart_company_overview` (PK: id, UK: corp_code)
- `dart_disclosure` (PK: id, UK: receipt_no)
- `dart_financial_statement` (PK: id, UK: corp_code+business_year+report_code+statement_type+account_name)
- `dart_financial_account` (PK: id, UK: corp_code+business_year+report_code)
- `dart_stock_quantity` (PK: id, UK: stock_code+business_year+report_code+stock_type) - OpenDART issued/treasury/distributed stock quantity.
- `dart_major_event` (PK: id, UK: receipt_no+event_type)

### News

- `news_keyword` (PK: id, UK: stock_code+keyword)
- `stock_news` (PK: id, UK: origin_url_hash)
- `news_ai_summary` (PK: id, UK: news_id)
- `news_sentiment` (PK: id, UK: news_id)

### AI

- `ai_prompt_log` (PK: id)
- `ai_decision_raw_response` (PK: id)
- `ai_decision` (PK: id)
- `ai_decision_factor` (PK: id)
- `ai_feedback` (PK: id, UK: ai_decision_id+evaluation_type)

### Account / Portfolio

- `account_balance` (PK: id)
- `portfolio_position` (PK: id, UK: account_no+stock_code)
- `portfolio_snapshot` (PK: id)
- `portfolio_profit_loss` (PK: id, UK: account_no+stock_code+base_date+evaluation_type)
- `portfolio_realized_profit_loss` (PK: id, UK: order_execution_id)
- `paper_account_balance` (PK: id)
- `paper_portfolio_position` (PK: id, UK: account_no+stock_code)
- `paper_portfolio_profit_loss` (PK: id, UK: account_no+stock_code+base_date+evaluation_type)

PAPER table rule:

- PAPER virtual account, position, and daily P/L state must use `paper_account_balance`, `paper_portfolio_position`, and `paper_portfolio_profit_loss`.
- REAL/KIS synced account, position, and daily P/L state must continue to use `account_balance`, `portfolio_position`, and `portfolio_profit_loss`.
- `risk_check_result.trading_mode` must be written on every risk check.
- Orders must only accept a passed risk check from the current trading mode.

### Orders / Executions

- `order_request` (PK: id, UK: idempotency_key)
- `order_execution` (PK: id)
- `order_cancel_request` (PK: id)
- `order_status_history` (PK: id)

### Risk / Strategy

- `risk_policy_config` (PK: id, UK: policy_code)
- `risk_check_result` (PK: id)
- `strategy_config` (PK: id, UK: strategy_code)
- `strategy_execution_log` (PK: id)

### Operation Logs

- `scheduler_execution_log` (PK: id)
- `broker_api_log` (PK: id)
- `external_api_call_log` (PK: id)
- `system_error_log` (PK: id)

## Important Enums / Constants

| Table | Column | Allowed values |
| --- | --- | --- |
| stock_master | market_type | KOSPI, KOSDAQ, UNKNOWN |
| stock_master | is_watched | 0, 1 |
| stock_candidate_score | candidate_status | CANDIDATE, AI_SELECTED, FALLBACK_SELECTED |
| ai_decision | decision | BUY, SELL, HOLD |
| ai_decision | risk_level | LOW, MEDIUM, HIGH |
| ai_decision | decision_status | CREATED, RISK_PASSED, RISK_FAILED, ORDERED, EXPIRED |
| order_request | order_side | BUY, SELL |
| order_request | order_type | MARKET, LIMIT |
| order_request | order_status | READY, ORDERED, PARTIALLY_FILLED, FILLED, FAILED, CANCELLED |
| risk_check_result | trading_mode | PAPER, REAL |
| ai_feedback | evaluation_type | DAILY, WEEKLY, MONTHLY, HOLDING_END |
| news_sentiment | sentiment | POSITIVE, NEUTRAL, NEGATIVE |
| ai_decision_factor | factor_type | TECHNICAL, NEWS, DART, FUNDAMENTAL, SUPPLY_DEMAND |
| ai_decision_factor | factor_direction | POSITIVE, NEGATIVE, NEUTRAL |
| external_api_call_log | provider | KIS, DART, NAVER, OPENAI |

## Java Mapping

- `DECIMAL` -> `BigDecimal`
- `BIGINT id` -> `Long`
- `INT` -> `Integer`
- `TINYINT(1)` -> `Boolean`
- `VARCHAR` -> `String`
- `DATE` -> `LocalDate`
- `DATETIME` -> `LocalDateTime`
- `JSON` -> `String` unless a TypeHandler is explicitly added.
