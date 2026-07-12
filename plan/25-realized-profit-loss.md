# Plan 25: SELL 체결별 실현손익

## Understanding

`portfolio_profit_loss`는 계좌/종목/일자/평가타입 집계용 UK를 갖고 있어 하루 여러 SELL 체결별 실현손익 저장에 적합하지 않다.

## Implementation Plan

- 신규 테이블 `portfolio_realized_profit_loss`를 추가한다.
- SELL 체결 저장 시 체결 전 포지션의 평균매수가 기준으로 실현손익을 계산한다.
- `portfolio_profit_loss`는 DAILY/WEEKLY/MONTHLY 집계용으로 유지한다.
- 스키마 변경 내용을 `AGENTS.md`, `.github/instructions/schema.instructions.md`에 반영한다.

## Files / Changes

- `feedback/domain/PortfolioRealizedProfitLoss.java`
- `feedback/mapper/PortfolioRealizedProfitLossMapper.java/.xml`
- `feedback/service/RealizedProfitLossService.java`
- `order/service/ExecutionSyncService.java`
- `AGENTS.md`
- `.github/instructions/schema.instructions.md`

## DB Schema

신규 테이블:

```sql
CREATE TABLE IF NOT EXISTS portfolio_realized_profit_loss (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_execution_id BIGINT NOT NULL,
  order_request_id BIGINT NOT NULL,
  broker_order_no VARCHAR(64) NULL,
  account_no VARCHAR(32) NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  executed_quantity INT NOT NULL,
  average_buy_price DECIMAL(20,4) NULL,
  executed_price DECIMAL(20,4) NOT NULL,
  buy_amount DECIMAL(20,4) NULL,
  sell_amount DECIMAL(20,4) NOT NULL,
  realized_profit_loss DECIMAL(20,4) NULL,
  return_rate DECIMAL(10,4) NULL,
  realized_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_portfolio_realized_pl_execution (order_execution_id),
  KEY idx_portfolio_realized_pl_account_date (account_no, realized_at),
  KEY idx_portfolio_realized_pl_stock_date (stock_code, realized_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Test Steps

- SELL 체결 1건 저장 시 실현손익 1건 저장.
- 같은 체결을 재처리해도 중복 저장되지 않음.
- 하루 여러 SELL 체결 저장 가능.

## Risks / Assumptions

- 수수료/세금은 현재 계산에서 제외하고, 추후 KIS 체결 응답 매핑이 보강되면 반영한다.
