# Plan 16: FeedbackService 단위 테스트

## Understanding

Plan 14 Test Steps 1~10번 — `FeedbackService`의 핵심 비즈니스 로직 단위 테스트.

대상 메서드 (Plan 14에서 정의/구현됨):

- `evaluateDecision(Long aiDecisionId, EvaluationType type)` — DAILY 평가
- `evaluateHoldingDayEnd(Long aiDecisionId)` — 보유기간 만기 평가
- `evaluateWeekly(LocalDate weekEnd)` — 주간 집계
- `evaluateMonthly(YearMonth month)` — 월간 집계

전제: Plan 15 (테스트 인프라) 완료. 이 플랜은 **Mockito 기반 순수 단위 테스트**라 DB 불필요.

---

## Implementation Plan

### Mock 대상

- `AiDecisionMapper`
- `AiFeedbackMapper`
- `PortfolioProfitLossMapper`
- `StockPriceDailyMapper`
- `AiPeriodicSummaryMapper`
- `BrokerClient` (현재가 조회)
- `OpenAiDecisionClient` (요약 LLM 호출 — 통계 → 자연어)

### 테스트 클래스

`src/test/java/com/bowon/cpm/feedback/service/FeedbackServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {
    @Mock AiDecisionMapper aiDecisionMapper;
    @Mock AiFeedbackMapper aiFeedbackMapper;
    @Mock PortfolioProfitLossMapper portfolioProfitLossMapper;
    @Mock StockPriceDailyMapper stockPriceDailyMapper;
    @Mock AiPeriodicSummaryMapper aiPeriodicSummaryMapper;
    @Mock BrokerClient brokerClient;
    @Mock OpenAiDecisionClient openAiDecisionClient;

    @InjectMocks FeedbackService feedbackService;
}
```

---

### Case 1. `evaluateDecision_DAILY_BUY_목표가도달_성공`

**Given**
- `ai_decision`: BUY, currentPrice=10000, targetPrice=11000, stopLossPrice=9000
- 현재가 조회 결과 = 11500 (목표가 초과)

**When** `evaluateDecision(id, DAILY)`

**Then**
- `targetReached=true`, `stopLossReached=false`, `success=true`
- `returnRate ≈ +15.0%`
- `aiFeedbackMapper.insertIgnore(...)` 1회 호출, 전달 객체의 필드 검증

---

### Case 2. `evaluateDecision_DAILY_BUY_손절가도달_실패`

**Given**
- BUY, currentPrice=10000, targetPrice=11000, stopLossPrice=9000
- 현재가 = 8800

**Then**
- `stopLossReached=true`, `success=false`, `returnRate ≈ -12.0%`

---

### Case 3. `evaluateDecision_DAILY_SELL_역방향성공`

**Given**
- SELL, currentPrice=10000, targetPrice=9000 (하락 목표), stopLossPrice=11000
- 현재가 = 8800

**Then**
- SELL 기준 `targetReached = (current <= targetPrice)` = true
- `stopLossReached = (current >= stopLossPrice)` = false
- `success=true`

---

### Case 4. `evaluateDecision_currentPrice조회실패_basePrice대체`

**Given**
- `brokerClient.getCurrentPrice(...)` → 예외 throw 또는 null
- basePrice (= ai_decision.current_price) = 10000

**Then**
- 평가가 중단되지 않고 `evaluatedPrice = basePrice`로 폴백
- `returnRate = 0`, `success=false` (또는 정책상 null) — 실제 구현 시그니처에 맞춰 검증

---

### Case 5. `evaluateDecision_중복저장_INSERT_IGNORE_검증`

**Given**
- 동일 `aiDecisionId + evaluationType=DAILY` 이미 존재 (`findByDecisionIdAndType` → Optional 반환)

**Then**
- 정책: 이미 존재해도 `insertIgnore` 호출은 한다 (UK가 막음) **또는** 사전 체크 후 skip — 구현 확인 필요.
- 두 케이스 모두 **결과적으로 중복 행이 생기지 않음**을 검증.
- 호출 횟수가 0 또는 1임을 검증.

---

### Case 6. `evaluateHoldingDayEnd_기간중최고가_목표가도달_성공`

**Given**
- BUY 판단, createdAt=2026-05-01, expectedHoldingDays=20 → 만기 2026-05-21
- targetPrice=11000, stopLossPrice=9000, basePrice=10000
- `stockPriceDailyMapper.findHighLowInRange(stockCode, 2026-05-01, 2026-05-21)` → high=11500, low=9500
- 만기일 종가 = 10500

**Then**
- `highestPrice=11500`, `lowestPrice=9500`, `evaluatedPrice=10500`
- `targetReached=true` (high ≥ target), `stopLossReached=false`
- `success=true`, `returnRate ≈ +5.0%`
- `ai_feedback` `evaluationType=HOLDING_END`로 저장

---

### Case 7. `evaluateHoldingDayEnd_기간중최저가_손절도달_실패`

**Given**
- BUY, target=11000, stopLoss=9000, base=10000
- 기간 high=10800 (목표 미도달), low=8700 (손절 도달)

**Then**
- `targetReached=false`, `stopLossReached=true`, `success=false`

---

### Case 8. `evaluateHoldingDayEnd_미만기_제외`

**Given**
- createdAt=오늘, expectedHoldingDays=20 → 만기 미래
- 또는 `findHoldingDayMaturedDecisions()`가 빈 리스트 반환 (스케줄러 측 처리)

**Then**
- 평가 메서드가 호출되더라도 만기 미도래면 `insertIgnore` 호출되지 않음
- 또는 별도 가드 (`if (maturityDate > today) return;`) 동작 확인

---

### Case 9. `evaluateWeekly_집계통계_정확성`

**Given**
- 주간 범위 내 `ai_feedback` 통계 mock:
  - 전체 10건, success=true 6건 → 승률 60%
  - returnRate: [+5, +3, -2, +8, +1, -1, +12, -4, +2, 0] → 평균 +2.4, max +12, min -4
  - target_reached 4건 / stop_loss_reached 2건
  - confidence 구간별 그룹 mock
- `openAiDecisionClient` 호출 → "지난주 BUY 평균 수익률 +2.4%..." 응답

**Then**
- 집계 계산 정확성 (평균/MAX/MIN/비율)
- `portfolioProfitLossMapper.insert(...)` `evaluationType=WEEKLY` 1회 호출
- `aiPeriodicSummaryMapper.insertIgnore(...)` 1회 호출 (summaryType=WEEKLY, period_start/end)

---

### Case 10. `evaluateMonthly_집계통계_정확성`

**Given**
- 월간 범위 내 통계 mock (Case 9 확장)
- 모델별 그룹, BUY/SELL/HOLD 분포, 주문 실패율, 리스크 실패 사유 TOP N

**Then**
- 집계 정확성
- `aiPeriodicSummaryMapper.insertIgnore(summaryType=MONTHLY)` 호출
- LLM 자연어 요약 결과가 `llm_summary`에 들어감

---

## Files / Changes

### 신규
- `src/test/java/com/bowon/cpm/feedback/service/FeedbackServiceTest.java`

### 수정
- (선택) `support/fixture/AiDecisionFixture.java`에 케이스별 빌더 메서드 추가

### DB
- 변경 없음 (순수 Mockito)

---

## Test Steps

```bash
./gradlew test --tests "com.bowon.cpm.feedback.service.FeedbackServiceTest"
```

- 10개 테스트 모두 GREEN
- 커버리지: `FeedbackService` 라인 ≥ 80%

---

## Risks / Assumptions

- **가정**: `FeedbackService`의 메서드 시그니처와 의존성 주입 구조가 위 Mock 목록과 일치. 다르면 실제 코드를 먼저 확인하고 Mock 보강.
- **위험**: Case 5의 중복 저장 정책이 구현마다 다를 수 있음 — 실제 코드 확인 후 시나리오 확정.
- **위험**: Case 4의 fallback 정책(null vs basePrice)이 구현마다 다름 — 실제 코드 확인 후 검증식 조정.
- **위험**: BigDecimal 비교는 `compareTo` 사용 (스케일 차이로 `equals` 실패 가능).

---

## 진행 순서 제안

1. Plan 15 완료 선행
2. `FeedbackService` 실제 코드 시그니처 재확인
3. Case 1 → 5 (evaluateDecision) 작성
4. Case 6 → 8 (evaluateHoldingDayEnd) 작성
5. Case 9, 10 (Weekly/Monthly) 작성
6. → Plan 17 진입

