# Plan: 3단계 - 종목 데이터 저장 (stock_master, stock_price_daily)

## Understanding

목표:
1. KIS API에서 종목 일봉(stock_price_daily)을 조회하여 저장한다.
2. stock_master에 종목 기본 정보를 저장/관리한다.
3. 이미 완료된 account_balance, portfolio_position은 2단계에서 구현됨.

---

## Implementation Plan

### 1. KIS 일봉 조회 클라이언트
- `KisDailyPriceClient` — `/uapi/domestic-stock/v1/quotations/inquire-daily-price`
- TR_ID: `FHKST01010400` (실전투자 — 주식현재가 일자별)
- `KisDailyPriceResponse` DTO

### 2. stock_master 도메인 + Mapper
- `StockMaster` domain
- `StockMasterMapper` + XML
  - `upsert` (종목 없으면 INSERT, 있으면 UPDATE)
  - `findByStockCode`
  - `findAllActive`

### 3. stock_price_daily 도메인 + Mapper
- `StockPriceDaily` domain
- `StockPriceDailyMapper` + XML
  - `insertIgnore` (UK: stock_code + trade_date 중복 무시)
  - `findByStockCodeAndDateRange`

### 4. StockService — stock_master 관리
- `findByStockCode`, `upsertStockMaster`

### 5. MarketDataService 확장 — 일봉 저장 추가
- `fetchAndSaveDailyPrices(String stockCode)` 메서드 추가
- KIS 일봉 조회 → stock_price_daily 배치 저장
- stock_master에 종목 정보 upsert

### 6. Admin API 추가
- `GET /api/stocks/{stockCode}/prices/daily` — 일봉 조회 + 저장

---

## Files / Changes

```
stock/
  domain/StockMaster.java
  mapper/StockMasterMapper.java
  service/StockService.java

market/
  domain/StockPriceDaily.java
  mapper/StockPriceDailyMapper.java
  service/MarketDataService.java  ← fetchAndSaveDailyPrices 추가

broker/kis/
  KisDailyPriceClient.java
  dto/KisDailyPriceResponse.java

admin/
  MarketController.java  ← /prices/daily 엔드포인트 추가

resources/mapper/
  stock/StockMasterMapper.xml
  market/StockPriceDailyMapper.xml
```

---

## Test Steps

1. `GET /api/stocks/005930/prices/daily` 호출
2. `stock_price_daily` 테이블에 삼성전자 일봉 데이터 저장 확인
3. `stock_master` 테이블에 종목 기본 정보 upsert 확인
4. `broker_api_log`에 호출 이력 확인

---

## Risks / Assumptions

- KIS 일봉 API는 최근 30영업일 기준으로 응답 (더 긴 기간은 연속 조회 필요)
- TR_ID `FHKST01010400` — 실전투자용 일자별 시세 (확인 필요, 다를 경우 TODO 처리)
- `stock_master`의 market_type(KOSPI/KOSDAQ)은 KIS 일봉 응답에 없으므로 기본값 처리
- 대량 INSERT 시 `<foreach>` 배치 INSERT 사용

