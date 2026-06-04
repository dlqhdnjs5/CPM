# CPM 프로젝트 현재 진행 상황

## 전체 진행률

```
1단계  프로젝트 기본 구조       ✅ 완료
2단계  한국투자증권 API 연결     ✅ 완료
3단계  데이터 저장               ✅ 완료
4단계  DART / 뉴스 수집          ✅ 완료
5단계  AI 판단                   ✅ 완료 (BUY/SELL/HOLD 생성)
6단계  리스크 검증               ⚠️ BUY 위주 (SELL 룰 미구현)
7단계  주문 요청 / 주문 실행     ⚠️ BUY만 완료 (SELL 미구현)
8단계  체결 동기화               ⚠️ 체결 저장은 OK, SELL 실현손익 미기록
9단계  AI 피드백                 ✅ 완료
10단계 스케줄러                  ⚠️ TargetStopMonitorScheduler 누락
Plan 14 피드백 확장              ✅ 완료 (Phase 1~5)
Plan 15~18 테스트 작성           ✅ 완료 (27 tests, 0 failures)
Plan 19 매도 도입 설계           📋 작성 완료 → 사용자 승인 대기
```

> ⚠️ 표시는 SELL 미구현으로 인한 갭. 상세 내용은 `PROCESS-AI-ORDER.md` 3절 참조.
> Plan 19~24가 SELL 도입 단계별 작업.


---

## 구현된 기능 상세

### 1단계: 프로젝트 기본 구조

- Spring Boot 3.5 + Java 21 + MyBatis + MySQL 세팅
- 공통 응답 래퍼 (`ApiResponse`)
- 공통 예외 처리 (`GlobalExceptionHandler`)
- WebClient 설정 (KIS / DART / Naver / OpenAI 별도 Bean)
- 외부 API 호출 로그 구조 (`external_api_call_log`, `broker_api_log`)

---

### 2단계: 한국투자증권 API 연결

- KIS Access Token 발급 / 자동 갱신
- 현재가 조회 (`KisMarketClient`)
- 일봉 조회 (`KisDailyPriceClient`)
- 계좌 잔고 + 보유종목 조회 (`KisAccountClient`)
- 주문 실행 (`KisOrderClient`) — 실전 / 모의 TR ID 분리
- `BrokerClient` 인터페이스 → `KisBrokerClient` 구현체

---

### 3단계: 데이터 저장

- `stock_price_daily` — 일봉 저장
- `stock_realtime_quote` — 현재가 저장
- `account_balance` — 계좌 잔고 저장
- `portfolio_position` — 보유 종목 UPSERT

---

### 4단계: DART / 뉴스 수집

- DART corp_code 동기화 (`dart_corp_code`)
- 공시 수집 (`dart_disclosure`) — receipt_no 기준 중복 제거
- 네이버 뉴스 수집 (`stock_news`) — origin_url_hash SHA-256 기준 중복 제거

---

### 5단계: AI 판단

- OpenAI Responses API (`/v1/responses`) 연동
- JSON Schema Structured Output 적용
- AI 판단 생성 전체 플로우:
  - 프롬프트에 **일봉 / 뉴스 / 공시 / 계좌 정보 / 과거 피드백** 포함
  - `ai_prompt_log` → `ai_decision_raw_response` → `ai_decision` → `ai_decision_factor` 저장
- 고신뢰 BUY (confidence ≥ 0.8) → `gpt-4.1`로 재검토

---

### 6단계: 리스크 검증

- `risk_policy_config` 기반 룰 검증
- 검증 항목:
  - AI 신뢰도 / 목표가 방향 / 손절가 방향
  - 손익비 / 예상 손실률
  - 예수금 부족 여부
  - 종목 최대 비중 초과
- `risk_check_result` 저장 — 실패 시 주문 차단

---

### 7단계: 주문 요청 / 주문 실행

- `OrderPolicyEngine` — 주문 수량 / 금액 계산
  - 총자산 × AI 추천 비중 → min(목표금액, 예수금)
  - 1주 미만이면 예수금으로 1주 강제 시도
- `order_request` 생성 (READY) → KIS 주문 API 호출 → ORDERED
- `idempotency_key` 기반 중복 주문 방지
- `order_status_history` 상태 이력 기록

---

### 8단계: 체결 동기화

- KIS 당일 체결 내역 조회 → `order_execution` 저장
- `order_request` 상태: ORDERED → FILLED
- 체결 후 포트폴리오 자동 갱신 (KIS 잔고 재조회)
- 중복 체결 방지 (`broker_order_no` 기준)

---

### 9단계: AI 피드백

- `FeedbackService.evaluateDecision()` — AI 판단 결과 평가
  - 현재가 조회 → 수익률 계산
  - 목표가 도달 여부 / 손절가 도달 여부 판단
  - 성공 여부 판단 (목표가 도달 O + 손절가 도달 X)
  - `ai_feedback` 저장 (DAILY / WEEKLY / MONTHLY)
- `FeedbackService.saveDailyProfitLoss()` — 일간 수익률 → `portfolio_profit_loss` 저장
- AI 판단 프롬프트에 **과거 피드백 요약 자동 포함** → AI 판단 품질 개선

---

## 현재 API 목록

### 📊 시세 / 종목 데이터
```
GET  http://localhost:8080/api/stocks/{stockCode}/quote
GET  http://localhost:8080/api/stocks/{stockCode}/prices/daily?from=2025-01-01&to=2026-05-31
```

### 💰 계좌 / 포트폴리오
```
GET  http://localhost:8080/api/account/balance
GET  http://localhost:8080/api/account/positions
```

### 📰 뉴스 수집
```
POST http://localhost:8080/api/news/{stockCode}/fetch?keyword=삼성전자&display=30
GET  http://localhost:8080/api/news/{stockCode}?limit=20
```

### 📋 DART 공시
```
POST http://localhost:8080/api/dart/corp-codes/sync
POST http://localhost:8080/api/dart/{stockCode}/disclosures/fetch?from=2025-01-01&to=2026-05-31
GET  http://localhost:8080/api/dart/{stockCode}/disclosures?limit=10
```

### 🤖 AI 판단
```
POST http://localhost:8080/api/ai/decisions/{stockCode}
GET  http://localhost:8080/api/ai/decisions?stockCode=005930&limit=10
GET  http://localhost:8080/api/ai/decisions/{id}
```

### 🛡️ 리스크 검증
```
POST http://localhost:8080/api/risk/checks/{aiDecisionId}
```

### 📦 주문 / 체결
```
POST http://localhost:8080/api/orders/requests/{aiDecisionId}
GET  http://localhost:8080/api/orders/requests?stockCode=005930&limit=20
POST http://localhost:8080/api/orders/executions/sync
GET  http://localhost:8080/api/orders/executions?stockCode=005930&limit=20
```

### 📊 피드백
```
POST http://localhost:8080/api/feedback/decisions/{aiDecisionId}?type=DAILY
GET  http://localhost:8080/api/feedback/decisions/{aiDecisionId}
POST http://localhost:8080/api/feedback/portfolio/daily
```

---

## 전체 자동매매 흐름 (완성된 상태)

```
[데이터 수집]
일봉 수집 → 뉴스 수집 → 공시 수집
        ↓
[AI 판단]
프롬프트 생성 (일봉 + 뉴스 + 공시 + 계좌정보 + 과거피드백)
→ OpenAI API 호출
→ ai_decision 저장
        ↓
[리스크 검증]
risk_policy_config 룰 체크
→ risk_check_result 저장
→ 실패 시 주문 차단
        ↓
[주문 실행]
주문 수량 계산 (OrderPolicyEngine)
→ order_request 생성
→ KIS 주문 API 호출
→ order_execution 저장
        ↓
[포트폴리오 갱신]
체결 동기화 → account_balance + portfolio_position 갱신
        ↓
[피드백]
수익률 계산 → 목표가/손절가 도달 여부 → ai_feedback 저장
→ 다음 AI 판단 프롬프트에 피드백 포함
```

---

## 다음 단계

```
10단계  스케줄러 구현 (자동 실행)
```

위 흐름이 지금은 사람이 API를 직접 누르는 방식.  
스케줄러를 붙이면 서버가 혼자서 자동으로 전부 처리한다.

