## Understanding

KIS `inquire-investor` API를 사용해서 감시 대상 종목의 개인/외국인/기관 수급 데이터를 수집하고, AI 판단 프롬프트의 `supplyDemand` 섹션에 최신 확정 수급을 넣는다.

## DB Schema Change Proposal

### 변경 필요 이유

현재 AI 프롬프트의 `supplyDemand` 필드는 전부 null이다.
KIS 투자자 수급 API 응답을 저장하려면 종목별/영업일별 수급 테이블이 필요하다.

### 변경 대상

- table: `stock_supply_demand_daily`

### 신규 DDL 제안

```sql
CREATE TABLE stock_supply_demand_daily (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    close_price DECIMAL(18, 2) NULL,
    individual_net_buy_qty BIGINT NULL,
    foreign_net_buy_qty BIGINT NULL,
    institution_net_buy_qty BIGINT NULL,
    individual_net_buy_amount DECIMAL(20, 2) NULL,
    foreign_net_buy_amount DECIMAL(20, 2) NULL,
    institution_net_buy_amount DECIMAL(20, 2) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supply_demand_stock_date (stock_code, trade_date),
    KEY idx_supply_demand_stock_date (stock_code, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 영향 범위

- Mapper: `StockSupplyDemandDailyMapper`
- Service: `SupplyDemandService`
- Scheduler: `SupplyDemandSyncScheduler`
- API: `POST/GET /api/stocks/{stockCode}/supply-demand`
- AI Prompt: `supplyDemand` null 대신 최신 확정 수급 입력

### 사용자 승인 필요

DB에는 위 DDL을 적용해야 실제 수집/저장이 가능하다.

## Implementation Plan

1. KIS 투자자 수급 응답 DTO와 `KisInvestorClient`를 추가한다.
2. `stock_supply_demand_daily` 도메인/매퍼/XML을 추가한다.
3. `SupplyDemandService`로 KIS 응답을 파싱하고 upsert한다.
4. `MarketController`에 수동 수집/조회 API를 추가한다.
5. 평일 17:10에 감시 대상 종목 전체를 수집하는 스케줄러를 추가한다.
6. `AiDecisionService`와 `AiDecisionPromptBuilder`가 최신 수급 데이터를 프롬프트에 넣도록 연결한다.
7. 단위 테스트/컨트롤러 테스트를 추가한다.

## Test Steps

1. KIS 응답 파싱/저장 서비스 테스트
2. AI 프롬프트 supplyDemand 섹션 테스트
3. 수동 API 컨트롤러 테스트
4. 스케줄러 테스트

## Risks / Assumptions

- KIS 응답의 수급 금액 단위는 KIS 문서 기준 값을 그대로 저장한다.
- 이 API는 장중 실시간 수급이 아니라 최신 확정 수급으로 취급한다.
- 공매도 금액/비율은 이 API에서 제공하지 않으므로 계속 null로 둔다.
