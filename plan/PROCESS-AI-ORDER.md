# 현재 AI 판단 → 주문 프로세스 (BUY 전용)

> 이 문서는 **현재 코드 기준** 매수(BUY) 흐름을 빠짐없이 정리한 것이다.
> 매도(SELL) 미구현 갭은 마지막 섹션에 명시한다.

---

## 1. 전체 흐름 (한눈에)

```
[장중 30분 주기]
AiDecisionScheduler
  └─ for active stock:
        AiDecisionService.generateDecision(stockCode)
          ├─ 데이터 수집 (일봉/뉴스/공시/지표/계좌/피드백/주간월간요약)
          ├─ OpenAI 호출 → JSON 파싱
          └─ 저장: ai_prompt_log, ai_decision_raw_response,
                   ai_decision(decision=BUY/SELL/HOLD, status=CREATED),
                   ai_decision_factor

[장중 30분 주기, AI 5분 후]
OrderExecutionScheduler
  ├─ expireStaleDecisions(35분 경과 CREATED) → EXPIRED
  └─ findPendingBuyDecisions("CREATED", since=35분)   ← BUY만 픽업
        for each decision:
          RiskService.checkAndSave(decisionId)
            ├─ syncAccountBalance (KIS API, DB 저장)
            ├─ 종목 보유 평가금액 조회
            └─ RiskManager.check → null이면 통과 → risk_check_result 저장
          if 통과 → OrderService.placeOrder(decisionId)
            ├─ HOLD / HOLD_BY_REVIEW 차단
            ├─ 리스크 통과 재확인
            ├─ idempotency_key 생성/중복 차단
            ├─ KIS 잔고 조회 (실패 시 주문 중단)
            ├─ OrderPolicyEngine.calculate (BUY 공식: 총자산×weight, min(예수금))
            ├─ order_request 생성 (READY)
            └─ OrderExecutor.execute
                  ├─ orderSide로 분기 → KIS placeBuyOrder/placeSellOrder
                  ├─ broker_order_no 저장 → ORDERED
                  └─ broker_api_log 저장

[장중 10분 주기 + 16:35 최종]
ExecutionSyncScheduler
  └─ ExecutionSyncService.syncExecutions
        ├─ KIS 당일 체결 조회
        ├─ order_execution 저장 (중복 방지: broker_order_no)
        ├─ order_request: ORDERED → FILLED + status_history
        └─ portfolioService.syncAccountBalance
            (account_balance / portfolio_position 갱신)

[장 마감 후]
DailyFeedbackScheduler / HoldingDayFeedbackScheduler
WeeklyFeedbackScheduler / MonthlyFeedbackScheduler
  └─ FeedbackService 가 ai_feedback / ai_periodic_summary 생성
```

---

## 2. 컴포넌트 상세

### 2.1 `AiDecisionScheduler`
- cron: 09:30 / 매시 정각 10~14시 / 매시 30분 10~14시 / 15:00
- 모든 active stock 순회 → `AiDecisionService.generateDecision()`
- BUY/HOLD 카운트만 집계 (**SELL은 카운트조차 없음**)

### 2.2 `AiDecisionService.generateDecision(stockCode)`
- 트랜잭션 외부 (외부 API)
- 수집 데이터: 일봉 60일 / 뉴스 7일 / 공시·주요이벤트 3개월 / 지표 최신 / 재무 요약 / 계좌 잔고 / 실시간 현재가 / 과거 피드백(DAILY 제외) / 최신 WEEKLY·MONTHLY 요약
- 저장 (REQUIRES_NEW via `AiDecisionPersistService`):
  - `ai_prompt_log`
  - `ai_decision_raw_response`
  - `ai_decision` (decision=`BUY|SELL|HOLD`, status=`CREATED`)
  - `ai_decision_factor`
- 고신뢰 BUY (≥0.8) 재검토: 동일 프롬프트로 `gpt-4.1` 호출, 결과가 BUY 아니면 status=`HOLD_BY_REVIEW`

### 2.3 `OrderExecutionScheduler`
- cron: 09:35 / 매시 5분 10~15시 / 매시 35분 10~14시
- 35분 경과 CREATED → EXPIRED
- ⚠️ `AiDecisionMapper.findPendingBuyDecisions` SQL에 `AND decision = 'BUY'` **하드코딩**
- 통과한 BUY만 RiskService → OrderService 순으로 처리

### 2.4 `RiskService.checkAndSave(aiDecisionId)`
- `DEFAULT_RISK_POLICY` 정책 1건 로드
- `portfolioService.syncAccountBalance()` 1회 호출 → 잔고+포지션 동시 갱신
- 해당 종목 보유 평가금액 조회
- `RiskManager.check()` 위임 → `risk_check_result` 저장

### 2.5 `RiskManager.check()` — 룰
| # | 검증 | BUY 적용 | SELL 적용 |
|---|------|---------|----------|
| 0 | HOLD → 즉시 통과 | — | — |
| 1 | confidence ≥ 최소 | ✅ | ✅ |
| 2 | target > current AND stopLoss < current | ✅ | ❌ (BUY 분기) |
| 3 | riskRewardRatio ≥ 최소 | ✅ | ✅ |
| 4 | expectedLossRate ≥ 최대 한도 | ✅ | ✅ |
| 5 | 예수금 ≥ 현재가 (1주 가능?) | ✅ | ⚠️ SELL엔 의미 없음 |
| 6 | 종목 현재 비중 < 최대 비중 | ✅ (추가 매수 차단) | ⚠️ SELL은 매도라 무관 |
| 7 | 예상 주문금액 ≤ 최대 주문금액 | ✅ | ❌ (SELL 수량 계산 없음) |
- **SELL 전용 검증(보유 수량 존재 여부 / 매도 가능 수량 초과 여부)은 없음**

### 2.6 `OrderService.placeOrder(aiDecisionId)`
- `@Transactional` 단일
- HOLD / HOLD_BY_REVIEW 차단
- 리스크 통과 재확인
- `idempotency_key = {acc}:{code}:{decisionId}:{BUY|SELL}:{yyyyMMddHHmm}` → 중복 차단
- KIS 잔고 조회 (**실패 시 예외 throw — SELL에도 동일하게 막힘**)
- `OrderPolicyEngine.calculate(decision, totalAsset, availableCash)` → ⚠️ **BUY 공식 전용**
- `order_request` INSERT (READY) + `order_status_history`
- `OrderExecutor.execute()` 호출 (트랜잭션 내부 외부 API — 타임아웃 시 READY로 남음)

### 2.7 `OrderPolicyEngine.calculate()`
```
targetAmount = totalAsset × recommendedPortfolioWeight (없으면 availableCash)
orderAmount  = min(targetAmount, availableCash)
quantity     = floor(orderAmount / currentPrice)
if quantity == 0 && availableCash ≥ currentPrice → quantity = 1
```
- ⚠️ portfolio_position 미참조 → SELL 수량 계산 불가

### 2.8 `OrderExecutor.execute(orderRequest)`
- orderSide로 분기 → `placeBuyOrder` / `placeSellOrder` ✅
- 성공: READY → ORDERED, broker_order_no 저장, status_history
- 실패: READY → FAILED, ExternalApiException throw
- `broker_api_log` 항상 저장

### 2.9 `ExecutionSyncScheduler` / `ExecutionSyncService.syncExecutions()`
- 당일 KIS 체결 조회 → 신규 체결만 처리 (`broker_order_no` 기준 중복 방지)
- order_execution INSERT (orderSide=BUY/SELL — 매도도 정상 저장 가능)
- order_request → FILLED + status_history
- `portfolioService.syncAccountBalance()` 호출 → 잔고/포지션 갱신
- ⚠️ `portfolio_profit_loss` REALIZED 기록 없음 (실현 손익 미기록)

---

## 3. SELL 미구현 갭 (정확한 목록)

| # | 위치 | 현상 | 영향 |
|---|------|------|------|
| G1 | `AiDecisionMapper.findPendingBuyDecisions` SQL에 `decision='BUY'` 하드코딩 | AI가 SELL 만들어도 픽업 안 됨 | 35분 후 EXPIRED, 매도 영원히 안 됨 |
| G2 | `OrderPolicyEngine` 매수 공식만 존재 | SELL 수량 계산 불가 (보유 수량 미참조) | 예수금 기준 엉뚱한 수량 |
| G3 | `OrderService.placeOrder` 잔고 조회 실패 시 무조건 throw | SELL이어도 잔고 못 받으면 차단 | 매도 실패 |
| G4 | `RiskManager`에 SELL 전용 검증 없음 | 보유 없는 종목 SELL도 통과 가능 | KIS 거부 응답 |
| G5 | `TargetStopMonitorScheduler` 부재 | 목표가/손절가 도달해도 자동 매도 없음 | **수익 실현 / 손실 방어 안 됨** |
| G6 | `ExecutionSyncService`에서 SELL 체결 후 `portfolio_profit_loss(REALIZED)` 미기록 | 실현 손익이 DB에 남지 않음 | 사후 분석 불가 |
| G7 | `HoldingDayFeedbackScheduler` 만기 도래 시 평가만, 청산 없음 | 만기 종목 계속 보유 | 정책 결정 사항 (현재 정책: 청산 안 함) |

---

## 4. 결론

- **인프라 (`OrderExecutor`, `KisOrderClient`, `order_request.orderSide`, `order_execution.orderSide`) 는 SELL 대응 완료**.
- **흐름 상단 (스케줄러 픽업, 수량 계산, 리스크 룰) 은 BUY 전용 하드코딩**.
- 매도 풀 자동화를 위해 G1~G6 해결 필요. 다음 Plan 19에서 설계.

