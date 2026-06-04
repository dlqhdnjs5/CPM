# CPM (Copy Paste Money) - AI Agent 가이드

## 프로젝트 개요
주식 자동매매/매도 시스템. AI가 뉴스, 기술적 지표, DART 공시 등을 분석하여 매매 판단을 내리고 자동으로 주문 실행하는 시스템.

## 기술 스택
- Java 21
- Spring Boot 3.5.x
- Spring AI (OpenAI, Azure OpenAI)
- MySQL 8.x (utf8mb4)
- Lombok
- Gradle

## 워크플로우
1. **Plan 작성**: 모든 기능 개발 전 `plan/` 디렉토리에 마크다운 플랜 문서를 작성한다.
2. **승인**: 사용자의 승인을 받은 후 개발을 진행한다.
3. **개발**: 플랜대로 구현한다.

## 핵심 규칙
- 확실하지 않은 내용은 절대 임의로 작성하지 말고, 반드시 사용자에게 확인한다.
- plan/ 디렉토리에 항상 플랜을 먼저 작성한다.
- DB 스키마를 항상 숙지한 상태에서 개발한다 (INSTRUCTION.md 참조).

## DB 접속 정보
- Host: localhost
- Port: 3306
- Database: cpm
- Username: givememoney
- Password: givememoney1234

## 도메인 구조 (DB 기준)
1. **종목/시장 데이터** - stock_master, stock_price_daily, stock_price_minute, stock_realtime_quote, stock_orderbook, stock_indicator_daily, stock_indicator_minute, market_index
2. **OpenDART 데이터** - dart_corp_code, dart_company_overview, dart_disclosure, dart_financial_statement, dart_financial_account, dart_major_event
3. **뉴스 데이터** - news_keyword, stock_news, news_ai_summary, news_sentiment
4. **AI 판단 데이터** - ai_prompt_log, ai_decision_raw_response, ai_decision, ai_decision_factor, ai_feedback
5. **계좌/포트폴리오** - account_balance, portfolio_position, portfolio_snapshot, portfolio_profit_loss
6. **주문/체결** - order_request, order_execution, order_cancel_request, order_status_history
7. **리스크/전략** - risk_policy_config, risk_check_result, strategy_config, strategy_execution_log
8. **운영 로그** - scheduler_execution_log, broker_api_log, external_api_call_log, system_error_log

## 개발 설계

# 돈복사 프로젝트 Agent 지시문

## 1. 프로젝트 개요

이 프로젝트는 **돈복사 프로젝트**라는 이름의 주식 자동매매 서버 애플리케이션이다.

목표는 주식 시세, 회사 정보, 차트 데이터, 뉴스, 공시, 재무 정보를 수집하고, OpenAI API를 통해 AI 매매 판단을 생성한 뒤, 한국투자증권 Open API를 사용하여 자동 주문까지 수행하는 서버형 자동매매 시스템을 개발하는 것이다.

단, AI가 단독으로 주문을 실행하면 안 된다.

최종 주문은 반드시 다음 흐름을 통과해야 한다.

```text
데이터 수집
→ 기술적 지표 계산
→ AI 판단 생성
→ 룰 기반 리스크 검증
→ 주문 정책 계산
→ 한국투자증권 Open API 주문 실행
→ 체결 결과 저장
→ 포트폴리오 갱신
→ 수익률 평가
→ AI 피드백 생성
```

핵심 원칙은 다음과 같다.

```text
AI가 판단한다.
Risk Manager가 검증한다.
Order Policy Engine이 주문 수량과 방식을 결정한다.
Broker Adapter가 한국투자증권 Open API로 주문한다.
모든 판단과 결과는 MySQL에 저장한다.
```

---

## 2. 기술 스택

다음 기술 스택을 기준으로 개발한다.

```text
JDK 21
Spring Boot
Spring Web
Spring Scheduler
Spring Batch
MyBatis
MySQL 8
WebClient
Resilience4j
Docker
Nginx
```

ORM/SQL 접근 방식은 **MyBatis를 기본으로 사용한다.**

### MyBatis를 사용하는 이유

이 프로젝트는 다음 특성이 강하다.

- 시계열 가격 데이터 조회
- 기간별 수익률 계산
- 종목별/전략별/기간별 집계
- AI 판단 이력 분석
- 체결 이력 기반 포트폴리오 계산
- 대량 데이터 insert/update
- 복잡한 조건 검색
- 인덱스를 의식한 SQL 튜닝 필요

따라서 JPA보다 SQL을 명확히 제어할 수 있는 MyBatis를 우선 사용한다.

JPA는 기본으로 사용하지 않는다.  
필요하면 추후 단순 설정성 테이블에만 제한적으로 검토한다.

---

## 3. 외부 API

초기 MVP에서는 아래 API만 사용한다.

```text
한국투자증권 Open API
OpenDART API
네이버 뉴스 검색 API
OpenAI API
```

### 3.1 한국투자증권 Open API

사용 목적:

- Access Token 발급/갱신
- 계좌 잔고 조회
- 예수금 조회
- 보유 종목 조회
- 현재가 조회
- 호가 조회
- 일봉/분봉 조회
- 매수 주문
- 매도 주문
- 주문 정정/취소
- 주문 체결 결과 조회
- 미체결 주문 조회
- WebSocket 기반 실시간 시세 수신

### 3.2 OpenDART API

사용 목적:

- corp_code 수집
- 종목 코드와 corp_code 매핑
- 기업 개황 조회
- 공시 목록 조회
- 주요 공시 수집
- 재무제표 수집
- 주요 이벤트 공시 탐지

### 3.3 네이버 뉴스 검색 API

사용 목적:

- 종목명 기반 뉴스 검색
- 회사명 기반 뉴스 검색
- 업종 키워드 뉴스 검색
- 뉴스 제목/요약/URL/발행일 저장
- 중복 뉴스 제거
- AI 뉴스 요약 및 감성 분석 입력 데이터 생성

### 3.4 OpenAI API

사용 목적:

- 종목 분석
- 뉴스 요약
- 공시 요약
- 재무제표 해석
- 기술적 지표 해석
- 매수/매도/보유 판단
- 목표가 판단
- 손절가 판단
- 예상 보유 기간 판단
- 추천 매수 비중 판단
- 리스크 요인 분석
- 과거 판단 결과 피드백 분석
- 전략 개선 리포트 생성
- JSON 기반 구조화 응답 생성

---

## 4. API Key 및 민감 정보 처리 규칙

API Key, Secret, Account Number, App Key, App Secret, OpenAI Key, 네이버 Client ID/Secret, DART API Key 등 민감 정보는 절대 코드에 하드코딩하지 않는다.

값을 정확히 알 수 없는 경우 임의로 추측하지 말고 아래와 같은 placeholder를 사용한다.

```yaml
kis:
  app-key: "찾아서 넣으세요"
  app-secret: "찾아서 넣으세요"
  account-no: "찾아서 넣으세요"

dart:
  api-key: "찾아서 넣으세요"

naver:
  client-id: "찾아서 넣으세요"
  client-secret: "찾아서 넣으세요"

openai:
  api-key: "찾아서 넣으세요"
```

환경변수 사용 예시는 다음 형식을 우선한다.

```yaml
kis:
  app-key: ${KIS_APP_KEY:찾아서 넣으세요}
  app-secret: ${KIS_APP_SECRET:찾아서 넣으세요}
  account-no: ${KIS_ACCOUNT_NO:찾아서 넣으세요}

dart:
  api-key: ${DART_API_KEY:찾아서 넣으세요}

naver:
  client-id: ${NAVER_CLIENT_ID:찾아서 넣으세요}
  client-secret: ${NAVER_CLIENT_SECRET:찾아서 넣으세요}

openai:
  api-key: ${OPENAI_API_KEY:찾아서 넣으세요}
```

민감 정보는 다음 위치에 넣지 않는다.

- 소스 코드
- 테스트 코드
- README
- AGENTS.md
- Git에 커밋되는 application.yml

로컬 개발용 값은 `application-local.yml` 또는 환경변수로 처리한다.  
단, 실제 키 값은 사용자가 직접 넣는다.

---

## 5. DB 스키마 변경 규칙

현재 사용자는 MySQL DDL을 직접 생성하고 있다.

DB 스키마는 프로젝트의 핵심 계약이다.

따라서 다음 규칙을 반드시 지킨다.

```text
DB 스키마 변경이 필요하면 반드시 사용자에게 먼저 보고한다.
사용자 승인 없이 컬럼 추가, 컬럼 삭제, 타입 변경, 인덱스 변경, 테이블 삭제를 하지 않는다.
```

스키마 변경이 필요할 경우 다음 형식으로 먼저 제안한다.

```md
## DB 스키마 변경 제안

### 변경 필요 이유

...

### 변경 대상

- table:
- column:
- index:

### 변경 전

```sql
...
```

### 변경 후

```sql
...
```

### 영향 범위

- Mapper:
- Service:
- Scheduler:
- API:
- 기존 데이터 영향:

### 사용자 승인 필요

이 변경을 적용해도 되는지 확인 필요.
```

사용자가 승인하기 전까지는 기존 스키마를 기준으로만 코드를 작성한다.

---

## 6. 아키텍처 방향

초기 MVP는 MSA로 만들지 않는다.

반드시 **모듈형 모놀리식** 구조로 개발한다.

패키지는 다음 구조를 기준으로 한다.

```text
com.bowon.cpm
 ├─ CpmApplication.java
 ├─ common
 │   ├─ config
 │   ├─ exception
 │   ├─ response
 │   └─ util
 ├─ stock
 │   ├─ domain
 │   ├─ mapper
 │   ├─ service
 │   └─ controller
 ├─ market
 │   ├─ kis
 │   ├─ indicator
 │   ├─ mapper
 │   └─ service
 ├─ dart
 │   ├─ client
 │   ├─ domain
 │   ├─ mapper
 │   └─ service
 ├─ news
 │   ├─ client
 │   ├─ domain
 │   ├─ mapper
 │   └─ service
 ├─ ai
 │   ├─ client
 │   ├─ prompt
 │   ├─ parser
 │   ├─ domain
 │   ├─ mapper
 │   └─ service
 ├─ risk
 │   ├─ rule
 │   ├─ domain
 │   ├─ mapper
 │   └─ service
 ├─ order
 │   ├─ policy
 │   ├─ executor
 │   ├─ domain
 │   ├─ mapper
 │   └─ service
 ├─ broker
 │   ├─ BrokerClient.java
 │   ├─ kis
 │   └─ dto
 ├─ portfolio
 │   ├─ domain
 │   ├─ mapper
 │   └─ service
 ├─ feedback
 │   ├─ domain
 │   ├─ mapper
 │   └─ service
 ├─ scheduler
 └─ admin
```

---

## 7. 핵심 모듈 책임

### 7.1 stock

종목 기본 정보를 관리한다.

주요 테이블:

```text
stock_master
```

책임:

- 종목 목록 조회
- 종목 상세 조회
- 종목 코드 기준 검색
- corp_code 매핑 조회

---

### 7.2 market

한국투자증권 Open API에서 시세 데이터를 수집하고 저장한다.

주요 테이블:

```text
stock_price_daily
stock_price_minute
stock_realtime_quote
stock_orderbook
stock_indicator_daily
stock_indicator_minute
market_index
```

책임:

- 현재가 조회
- 일봉 조회
- 분봉 조회
- 호가 조회
- 실시간 시세 저장
- 기술적 지표 계산

---

### 7.3 dart

OpenDART 데이터를 수집하고 저장한다.

주요 테이블:

```text
dart_corp_code
dart_company_overview
dart_disclosure
dart_financial_statement
dart_financial_account
dart_major_event
```

책임:

- corp_code 수집
- 기업 개황 수집
- 공시 목록 수집
- 재무제표 수집
- 주요 이벤트 공시 분류

---

### 7.4 news

네이버 뉴스 검색 API 데이터를 수집하고 저장한다.

주요 테이블:

```text
news_keyword
stock_news
news_ai_summary
news_sentiment
```

책임:

- 종목별 뉴스 검색
- 중복 뉴스 제거
- 뉴스 저장
- 뉴스 요약 요청 대상 선정
- 뉴스 감성 분석 결과 저장

주의:

`stock_news.origin_url`은 길이가 길 수 있으므로 직접 unique index를 걸지 않는다.  
`origin_url_hash`를 사용해서 중복을 제거한다.

---

### 7.5 ai

OpenAI API를 호출하여 AI 판단을 생성한다.

주요 테이블:

```text
ai_prompt_log
ai_decision_raw_response
ai_decision
ai_decision_factor
ai_feedback
```

책임:

- AI 프롬프트 생성
- OpenAI API 호출
- JSON 응답 파싱
- 파싱 실패 처리
- AI 판단 저장
- AI 판단 근거 저장
- 원문 응답 저장
- 피드백 리포트 생성

AI 응답은 반드시 JSON 구조로 받아야 한다.

---

### 7.6 risk

AI 판단 결과를 검증한다.

주요 테이블:

```text
risk_policy_config
risk_check_result
```

책임:

- AI 신뢰도 검증
- 예수금 초과 검증
- 종목별 최대 비중 검증
- 목표가/손절가 타당성 검증
- 손익비 검증
- 유동성 검증
- 거래정지/관리종목/투자경고 종목 차단
- 리스크 검증 결과 저장

---

### 7.7 order

주문 정책을 계산하고 주문 요청을 생성한다.

주요 테이블:

```text
order_request
order_execution
order_cancel_request
order_status_history
```

책임:

- 주문 가능 금액 계산
- 주문 수량 계산
- 시장가/지정가 결정
- 주문 요청 생성
- 주문 중복 방지
- 주문 상태 변경 이력 저장
- 체결 결과 저장

---

### 7.8 broker

증권사 API 연동을 추상화한다.

초기 구현체는 한국투자증권만 만든다.

```java
public interface BrokerClient {

    AccountBalance getAccountBalance();

    List<PortfolioPosition> getPositions();

    StockQuote getCurrentPrice(String stockCode);

    List<DailyPrice> getDailyPrices(String stockCode, LocalDate from, LocalDate to);

    OrderResult placeBuyOrder(OrderCommand command);

    OrderResult placeSellOrder(OrderCommand command);

    OrderStatus getOrderStatus(String brokerOrderNo);

    List<ExecutionResult> getExecutions(LocalDate date);
}
```

구현체:

```java
KisBrokerClient implements BrokerClient
```

한국투자증권 전용 로직은 `broker.kis` 내부에만 둔다.  
서비스 레이어는 반드시 `BrokerClient` 인터페이스를 통해 호출한다.

---

### 7.9 portfolio

계좌와 포트폴리오 상태를 관리한다.

주요 테이블:

```text
account_balance
portfolio_position
portfolio_snapshot
portfolio_profit_loss
```

책임:

- 계좌 잔고 저장
- 예수금 저장
- 보유 종목 갱신
- 평가금액 계산
- 수익률 계산
- 포트폴리오 스냅샷 생성

---

### 7.10 feedback

AI 판단 결과를 평가한다.

주요 테이블:

```text
ai_feedback
portfolio_profit_loss
```

책임:

- 일간 평가
- 주간 평가
- 월간 평가
- 목표가 도달 여부 계산
- 손절가 도달 여부 계산
- 판단 성공 여부 계산
- 다음 AI 판단에 사용할 피드백 요약 생성

---

## 8. AI 판단 JSON 구조

AI 판단 결과는 다음 형태를 기준으로 처리한다.

```json
{
  "stockCode": "005930",
  "stockName": "삼성전자",
  "decision": "BUY",
  "confidence": 0.82,
  "currentPrice": 75000,
  "targetPrice": 82000,
  "stopLossPrice": 71500,
  "expectedReturnRate": 9.33,
  "expectedLossRate": -4.66,
  "riskRewardRatio": 2.0,
  "recommendedPortfolioWeight": 0.15,
  "expectedHoldingDays": 20,
  "buyStrategy": {
    "type": "SPLIT_BUY",
    "splitCount": 3,
    "reason": "단기 변동성이 있어 분할 매수가 적합함"
  },
  "sellStrategy": {
    "takeProfitType": "TARGET_PRICE",
    "stopLossType": "STOP_LOSS_PRICE",
    "reason": "목표가 도달 시 익절, 손절가 이탈 시 매도"
  },
  "analysis": {
    "summary": "기술적 반등 구간이며 뉴스와 수급이 긍정적임",
    "positiveFactors": [
      "거래량 증가",
      "기관 순매수",
      "업황 개선 뉴스"
    ],
    "negativeFactors": [
      "단기 저항선 근접",
      "시장 변동성 확대"
    ],
    "technicalAnalysis": "20일 이동평균선을 상향 돌파했고 RSI는 과열 전 단계임",
    "newsAnalysis": "최근 반도체 업황 회복 뉴스가 긍정적으로 작용함",
    "disclosureAnalysis": "최근 공시에서 특별한 부정 이슈는 확인되지 않음",
    "fundamentalAnalysis": "실적 개선 기대감이 존재함",
    "supplyDemandAnalysis": "기관과 외국인 수급이 개선되는 흐름임"
  },
  "risk": {
    "riskLevel": "MEDIUM",
    "mainRisks": [
      "시장 전체 조정",
      "목표가 대비 손절가가 가까움"
    ],
    "invalidCondition": "종가 기준 71500원 이탈 시 판단 무효"
  },
  "reason": "기술적 지표, 뉴스, 수급이 모두 단기 상승 가능성을 지지함"
}
```

AI 응답 파싱 실패 시 다음 처리를 한다.

```text
1. ai_decision_raw_response에 원문 저장
2. parse_error 저장
3. 재시도 가능 여부 판단
4. 재시도 실패 시 ai_decision 생성하지 않음
5. system_error_log에 기록
```

---

## 9. 자동매매 정책

AI가 BUY를 반환해도 바로 주문하면 안 된다.

최종 주문은 반드시 다음 단계를 통과해야 한다.

```text
AI Decision
→ Risk Check
→ Order Policy Calculation
→ Order Request
→ Broker Order Execution
→ Execution Sync
→ Portfolio Update
```

### 9.1 AI 판단 단계

AI는 다음 값을 생성한다.

```text
decision
confidence
currentPrice
targetPrice
stopLossPrice
expectedReturnRate
expectedLossRate
riskRewardRatio
recommendedPortfolioWeight
expectedHoldingDays
reason
```

### 9.2 Risk Manager 검증 단계

다음 조건을 검증한다.

```text
AI 신뢰도 기준 이상인지
목표가가 현재가보다 높은지
손절가가 현재가보다 낮은지
손익비가 기준 이상인지
예상 손실률이 과도하지 않은지
계좌 예수금 범위 안인지
종목별 최대 비중을 넘지 않는지
전체 포트폴리오가 특정 종목에 과집중되지 않는지
거래정지/관리종목/투자경고 종목이 아닌지
유동성이 부족하지 않은지
비정상 급등 종목을 추격 매수하는 상황이 아닌지
최소 주문 수량이 가능한지
```

검증 실패 시 주문하지 않고 `risk_check_result`에 실패 사유를 저장한다.

### 9.3 Order Policy Engine 단계

리스크 검증 통과 후 실제 주문 조건을 계산한다.

```text
주문 기준 금액 = 총 평가자산 × AI 추천 비중
실제 주문 가능 금액 = min(주문 기준 금액, 예수금, 종목별 최대 허용 금액)
주문 수량 = floor(실제 주문 가능 금액 / 현재가)
```

주문 수량이 1 미만이면 주문하지 않는다.

### 9.4 주문 실행 단계

`order_request`를 먼저 생성한다.  
그 후 `OrderExecutor`가 한국투자증권 Open API를 호출한다.

주문 성공 시:

```text
order_request.order_status = ORDERED
broker_order_no 저장
order_status_history 저장
```

체결 확인 시:

```text
order_execution 저장
portfolio_position 갱신
account_balance 갱신
```

---

## 10. 매매 횟수 제한 정책

매매 횟수 제한은 두지 않는다.

대신 다음 조건으로만 통제한다.

```text
지정된 계좌의 예수금
보유 종목 평가금액
종목별 투자 비중
포트폴리오 전체 리스크
AI 판단 신뢰도
AI가 제시한 목표가/손절가 타당성
주문 가능 금액
주문 가능 수량
```

계좌 내 금액을 초과하는 주문은 절대 실행하지 않는다.

---

## 11. 스케줄러 설계

초기 MVP에서는 Spring Scheduler를 사용한다.

### 장 시작 전

```text
StockMasterSyncScheduler
AccountSyncScheduler
DailyPriceSyncScheduler
DartCollectScheduler
NewsCollectScheduler
WatchlistBuildScheduler
```

### 장중

```text
RealtimeQuoteScheduler
MinutePriceSyncScheduler
IndicatorCalculateScheduler
AiDecisionScheduler
RiskCheckScheduler
OrderExecutionScheduler
ExecutionSyncScheduler
TargetStopMonitorScheduler
```

### 장 마감 후

```text
DailyPriceFinalizeScheduler
PortfolioSnapshotScheduler
DailyFeedbackScheduler
```

### 주간/월간

```text
WeeklyFeedbackScheduler
MonthlyFeedbackScheduler
StrategyPerformanceScheduler
```

모든 스케줄러 실행 결과는 `scheduler_execution_log`에 저장한다.

스케줄러는 중복 실행 방지 로직을 가져야 한다.

중복 실행 방지 방식:

```text
scheduler_name + started_at 기준 실행 중 여부 확인
또는 DB Lock 테이블 사용
또는 ShedLock 도입 검토
```

ShedLock을 도입하려면 먼저 사용자에게 의존성 추가 여부를 확인한다.

---

## 12. API 설계 방향

초기 Admin API는 다음 정도만 만든다.

```text
GET  /api/stocks
GET  /api/stocks/{stockCode}
GET  /api/stocks/{stockCode}/prices/daily
GET  /api/stocks/{stockCode}/news
GET  /api/stocks/{stockCode}/disclosures

POST /api/ai/decisions
GET  /api/ai/decisions
GET  /api/ai/decisions/{id}

POST /api/risk/checks
GET  /api/risk/checks/{id}

POST /api/orders/requests
GET  /api/orders/requests
GET  /api/orders/executions

GET  /api/account/balance
GET  /api/portfolio/positions
GET  /api/portfolio/profit-loss

PATCH /api/admin/trading-mode
GET   /api/admin/strategies
PUT   /api/admin/strategies/{strategyCode}
GET   /api/admin/risk-policy
PUT   /api/admin/risk-policy/{policyCode}
```

처음부터 화면을 만들 필요는 없다.  
먼저 REST API와 DB 흐름을 완성한다.

---

## 13. 개발 우선순위

다음 순서로 개발한다.

### 1단계: 프로젝트 기본 구조

```text
Spring Boot JDK 21 프로젝트 구성
MyBatis 설정
MySQL 연결
공통 응답/예외 구조
WebClient 공통 설정
외부 API 호출 로그 구조
```

### 2단계: 한국투자증권 API 연결

```text
KIS Access Token 발급/갱신
계좌 잔고 조회
보유 종목 조회
현재가 조회
일봉 조회
broker_api_log 저장
```

### 3단계: 데이터 저장

```text
stock_master 저장
stock_price_daily 저장
account_balance 저장
portfolio_position 저장
```

### 4단계: OpenDART / 뉴스 수집

```text
dart_corp_code 수집
dart_disclosure 저장
stock_news 저장
origin_url_hash 기반 중복 제거
```

### 5단계: AI 판단

```text
AI 프롬프트 생성
OpenAI API 호출
ai_prompt_log 저장
ai_decision_raw_response 저장
JSON 파싱
ai_decision 저장
ai_decision_factor 저장
```

### 6단계: 리스크 검증

```text
risk_policy_config 조회
RiskManager 구현
risk_check_result 저장
검증 실패 시 주문 차단
```

### 7단계: 가상 주문

```text
OrderPolicyEngine 구현
order_request 생성
실제 주문 없이 PAPER 모드 처리
가상 체결 저장
portfolio_position 갱신
```

### 8단계: 모의투자 주문

```text
한국투자증권 모의투자 주문 API 연결
실제 broker_order_no 저장
체결 결과 동기화
order_execution 저장
```

### 9단계: 피드백

```text
일간 수익률 계산
목표가 도달 여부 계산
손절가 도달 여부 계산
ai_feedback 저장
다음 AI 판단에 피드백 요약 포함
```

---

## 14. 코드 작성 규칙

### 14.1 변경 전 계획 작성

비단순 작업을 시작하기 전에는 먼저 계획을 작성한다.

형식:

```md
## Understanding

...

## Implementation Plan

...

## Files / Changes

...

## Test Steps

...

## Risks / Assumptions

...
```

사용자가 승인한 범위만 구현한다.

### 14.2 기존 구조 우선

새로운 라이브러리, 새로운 아키텍처, 새로운 패턴을 임의로 도입하지 않는다.

필요하면 먼저 제안한다.

### 14.3 최소 변경

요구사항과 관련 없는 리팩토링을 하지 않는다.

패키지명, 클래스명, 테이블명, 컬럼명을 임의로 바꾸지 않는다.

### 14.4 SQL 우선

MyBatis Mapper SQL은 명확하게 작성한다.

복잡한 쿼리는 인덱스를 고려한다.

기간 조회는 반드시 인덱스 컬럼을 조건에 포함한다.

### 14.5 로그 저장

외부 API 호출은 성공/실패 여부와 응답을 로그 테이블에 저장한다.

대상 테이블:

```text
broker_api_log
external_api_call_log
system_error_log
scheduler_execution_log
```

단, API Key, Secret, 계좌번호 전체값은 로그에 남기지 않는다.

마스킹한다.

예시:

```text
12345678-01 → 1234****-**
```

---

## 15. 예외 처리 규칙

외부 API 호출 실패 시 무조건 시스템을 죽이지 않는다.

기본 처리:

```text
1. 실패 로그 저장
2. 재시도 가능 오류인지 판단
3. 가능하면 재시도
4. 계속 실패하면 해당 작업만 FAILED 처리
5. 주문 관련 실패는 반드시 order_status_history에 남김
```

주문 API 실패는 특히 조심한다.

주문 요청 후 타임아웃이 발생하면 바로 재주문하지 않는다.

먼저 미체결/체결 조회 API로 실제 주문 접수 여부를 확인한다.

---

## 16. 주문 중복 방지 규칙

주문 요청은 반드시 `idempotency_key`를 가진다.

예시:

```text
{accountNo}:{stockCode}:{aiDecisionId}:{orderSide}:{yyyyMMddHHmm}
```

동일한 `idempotency_key`가 이미 존재하면 새 주문을 생성하지 않는다.

주문 API 타임아웃이 발생해도 같은 주문을 즉시 재시도하지 않는다.

먼저 기존 주문 상태를 확인한다.

---

## 17. MVP에서 하지 말 것

초기 MVP에서는 다음을 하지 않는다.

```text
MSA 분리
Kafka 도입
Vector DB 도입
Elasticsearch 도입
React Admin UI 대규모 개발
복잡한 포트폴리오 최적화
멀티 증권사 동시 지원
실전 계좌 대금 자동매매 즉시 적용
```

먼저 다음 흐름을 완성한다.

```text
데이터 수집
→ AI 판단 저장
→ 리스크 검증
→ 가상 주문
→ 피드백 저장
```

---

## 18. 최우선 구현 기능 5개

가장 먼저 구현할 기능은 다음이다.

```text
1. 한국투자증권 계좌 잔고/현재가 조회
2. stock_price_daily / stock_news / dart_disclosure 저장
3. OpenAI AI 판단 JSON 생성 및 ai_decision 저장
4. RiskManager 검증 로직
5. OrderRequest 생성까지의 가상매매 플로우
```

---

## 19. DB 테이블 기준

현재 사용자는 MySQL DDL을 이미 생성하고 있다.

코드는 현재 DB 스키마를 기준으로 작성한다.

스키마와 코드가 맞지 않을 경우 코드를 임의로 맞추지 말고 먼저 사용자에게 보고한다.

특히 다음 테이블은 핵심이다.

```text
stock_master
stock_price_daily
stock_price_minute
stock_realtime_quote
stock_orderbook
stock_indicator_daily
stock_indicator_minute
market_index

dart_corp_code
dart_company_overview
dart_disclosure
dart_financial_statement
dart_financial_account
dart_major_event

news_keyword
stock_news
news_ai_summary
news_sentiment

ai_prompt_log
ai_decision_raw_response
ai_decision
ai_decision_factor
ai_feedback

account_balance
portfolio_position
portfolio_snapshot
portfolio_profit_loss

order_request
order_execution
order_cancel_request
order_status_history

risk_policy_config
risk_check_result
strategy_config
strategy_execution_log

scheduler_execution_log
broker_api_log
external_api_call_log
system_error_log
```

---

## 20. 최종 목표

이 프로젝트의 최종 목표는 단순 자동매매 봇이 아니다.

최종 목표는 다음이다.

```text
AI 판단을 기록한다.
리스크 검증을 기록한다.
주문 요청을 기록한다.
체결 결과를 기록한다.
수익률 평가를 기록한다.
그 결과를 다시 AI 판단에 반영한다.
```

즉, 스스로 판단 품질을 평가하고 개선하는 자동매매 플랫폼을 만드는 것이 목표다.
