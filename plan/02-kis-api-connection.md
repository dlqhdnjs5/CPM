# Plan: 2단계 - 한국투자증권 API 연결

## Understanding

목표: KIS 실전투자 API에서 Access Token 발급/갱신, 현재가 조회, 계좌 잔고 조회를 구현하고 결과를 DB 로그에 저장한다.

기반:
- 1단계에서 KisProperties, WebClient Bean, 공통 예외/응답 구조 완성됨
- 실전투자 기준 (`https://openapi.koreainvestment.com:9443`)
- 모의투자 API 미사용

---

## Implementation Plan

### 1. broker/kis — KIS 클라이언트 레이어

| 클래스 | 역할 |
|--------|------|
| `KisAuthClient` | Access Token 발급/갱신 (메모리 캐시, 만료 10분 전 갱신) |
| `KisHeaderFactory` | 공통 요청 헤더 생성 (Authorization, appkey, appsecret, tr_id) |
| `KisMarketClient` | 현재가 조회 (`/uapi/domestic-stock/v1/quotations/inquire-price`) |
| `KisAccountClient` | 계좌 잔고/보유종목 조회 |
| `KisTokenResponse` | Token 응답 DTO |
| `KisCurrentPriceResponse` | 현재가 응답 DTO |
| `KisBalanceResponse` | 잔고/보유종목 응답 DTO |

### 2. broker — BrokerClient 인터페이스 + KisBrokerClient

| 클래스 | 역할 |
|--------|------|
| `BrokerClient` | 증권사 API 추상화 인터페이스 |
| `AccountBalanceResult` | 잔고 결과 DTO |
| `PortfolioPositionResult` | 보유종목 결과 DTO |
| `StockQuoteResult` | 현재가 결과 DTO |
| `KisBrokerClient` | BrokerClient 구현체 |
| `KisMapper` | KIS 응답 → Result 변환 |

### 3. market/mapper + service — 현재가 저장

- `StockRealtimeQuoteMapper` + XML → `stock_realtime_quote` INSERT
- `MarketDataService` → 현재가 조회 후 DB 저장

### 4. portfolio/mapper + service — 잔고/보유종목 저장

- `AccountBalanceMapper` + XML → `account_balance` INSERT
- `PortfolioPositionMapper` + XML → `portfolio_position` UPSERT
- `PortfolioService` → 잔고 동기화

### 5. 운영 로그 — broker_api_log 저장

- `BrokerApiLogMapper` + XML → `broker_api_log` INSERT
- 모든 KIS API 호출 성공/실패 시 로그 저장
- 계좌번호/API Key 마스킹 처리

### 6. admin API — 수동 트리거용 엔드포인트

- `GET /api/account/balance` → 잔고 조회 + 저장
- `GET /api/stocks/{stockCode}/quote` → 현재가 조회 + 저장

---

## Files / Changes

```
broker/
  BrokerClient.java
  AccountBalanceResult.java
  PortfolioPositionResult.java
  StockQuoteResult.java
  kis/
    KisAuthClient.java
    KisHeaderFactory.java
    KisMarketClient.java
    KisAccountClient.java
    KisBrokerClient.java
    KisMapper.java
    dto/
      KisTokenResponse.java
      KisCurrentPriceResponse.java
      KisBalanceResponse.java

market/
  mapper/StockRealtimeQuoteMapper.java
  service/MarketDataService.java

portfolio/
  mapper/AccountBalanceMapper.java
  mapper/PortfolioPositionMapper.java
  service/PortfolioService.java
  domain/AccountBalance.java
  domain/PortfolioPosition.java

admin/
  AccountController.java
  MarketController.java

common/
  mapper/BrokerApiLogMapper.java
  domain/BrokerApiLog.java

resources/mapper/
  market/StockRealtimeQuoteMapper.xml
  portfolio/AccountBalanceMapper.xml
  portfolio/PortfolioPositionMapper.xml
  common/BrokerApiLogMapper.xml
```

---

## Test Steps

1. `application-local.yml`에 KIS appKey, appSecret, accountNo 입력
2. `GET /api/account/balance` 호출 → DB account_balance 저장 확인
3. `GET /api/stocks/005930/quote` 호출 → DB stock_realtime_quote 저장 확인
4. `broker_api_log` 테이블에 호출 이력 저장 확인

---

## Risks / Assumptions

- KIS TR ID는 공식 문서 기준 실전투자 TR ID 사용 (모의투자 TR ID 미사용)
  - 현재가: `FHKST01010100`
  - 잔고: `TTTC8434R`
- TR ID가 정확하지 않으면 KIS에서 에러 응답 → `broker_api_log`에 저장 후 예외 처리
- Access Token은 하루 1회 발급 기준이므로 메모리 캐시 + 만료 전 갱신 처리
- 계좌번호 형식: `XXXXXXXXXX-XX` → `-` 기준 앞 부분이 CANO, 뒷 부분이 ACNT_PRDT_CD

