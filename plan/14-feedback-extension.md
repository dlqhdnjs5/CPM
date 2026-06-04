# Plan 14: 피드백 확장 (WEEKLY / MONTHLY / HoldingDay)

## Understanding

현재 피드백 시스템의 한계:

- `DailyFeedbackScheduler`만 동작 (당일 16:40, 오늘 BUY/SELL 판단 1회 평가)
- AI가 제시한 `expectedHoldingDays`와 무관하게 **당일 종가**로만 성패 판정
- WEEKLY / MONTHLY 평가 스케줄러 자체가 없음
- 결과적으로 AI 자기개선 루프가 미작동

사용자 승인 사항:

- **DAILY 피드백은 DB 저장만 하고, AI 판단 프롬프트에는 반영 안 함**
- **WEEKLY / MONTHLY / HoldingDay 피드백 결과만 AI 프롬프트에 반영**
- WEEKLY: 사용자가 제공한 6.x 스펙대로
- MONTHLY: 사용자가 제공한 7.x 스펙대로
- HoldingDay: 비슷한 형태로 자체 설계
- **테스트 케이스 작성 및 테스트 실시**

---

## Implementation Plan

### Phase 1. DAILY 피드백 격리 (AI 프롬프트에서 제외)

#### 1-1. `AiFeedbackMapper`에 `evaluation_type` 필터 메서드 추가

```xml
<select id="findRecentByStockCodeAndTypes" resultType="...">
  SELECT ... FROM ai_feedback
  WHERE stock_code = #{stockCode}
    AND evaluation_type IN
      <foreach collection="types" item="t" open="(" close=")" separator=",">#{t}</foreach>
  ORDER BY evaluated_at DESC
  LIMIT #{limit}
</select>
```

#### 1-2. `AiDecisionService`에서 호출 변경

```java
// before
aiFeedbackMapper.findRecentByStockCode(stockCode, 3);

// after — DAILY 제외
aiFeedbackMapper.findRecentByStockCodeAndTypes(
    stockCode, List.of("WEEKLY", "MONTHLY", "HOLDING_END"), 3);
```

---

### Phase 2. HoldingDayFeedback 설계

#### 2-1. 정의

AI가 판단할 때 제시한 `expected_holding_days`가 지난 시점에 그 판단의 최종 성과를
종합 평가하여 `ai_feedback`에 `evaluation_type='HOLDING_END'`로 저장.

#### 2-2. 실행 시점

매 거래일 장 마감 후 (17:00).

#### 2-3. 대상

다음 조건을 모두 만족하는 `ai_decision`:

```text
decision IN ('BUY', 'SELL')
DATE(created_at) + expected_holding_days <= 오늘
expected_holding_days IS NOT NULL
같은 ai_decision_id로 evaluation_type='HOLDING_END' 피드백이 아직 없음
```

#### 2-4. 계산 항목

```text
base_price                = ai_decision.current_price
evaluated_price           = 만기 시점(=오늘) 종가
highest_price             = 보유 기간 중 최고가 (stock_price_daily MAX)
lowest_price              = 보유 기간 중 최저가 (stock_price_daily MIN)
return_rate               = (evaluated_price - base_price) / base_price * 100
target_reached            = 기간 중 최고가 >= target_price (BUY 기준)
                            기간 중 최저가 <= target_price (SELL 기준)
stop_loss_reached         = 기간 중 최저가 <= stop_loss_price (BUY 기준)
                            기간 중 최고가 >= stop_loss_price (SELL 기준)
success                   = target_reached AND NOT stop_loss_reached
```

> 구할 수 없는 필드는 null 저장.

#### 2-5. AI 판단 개선용 요약 (feedback_summary)

예시:
```
[BUY 005930] 20일 보유 기간 종료: 수익률 +8.9%,
기간 중 최고 +12.3% (목표가 도달), 최저 -2.1% (손절가 미도달),
AI 예측 정확 → 성공
```

#### 2-6. 신규 클래스

- `HoldingDayFeedbackScheduler` (cron: `0 0 17 * * MON-FRI`)
- `FeedbackService.evaluateHoldingDayEnd(Long aiDecisionId)` 메서드
- `AiDecisionMapper.findHoldingDayMaturedDecisions()` — 만기 도래 + 미평가 판단 조회
- `StockPriceDailyMapper.findHighLowInRange(stockCode, from, to)` — 기간 OHLCV 집계

---

### Phase 3. WEEKLY 피드백

#### 3-1. 실행 시점

매주 금요일 장 마감 후 (17:30) — 토요일 새벽 대신 동일 영업일 처리.

#### 3-2. 대상

- 이번 주(월~금) 생성된 BUY/SELL 판단
- 이번 주에 `HOLDING_END` 평가가 완료된 판단

#### 3-3. 계산 항목 (구할 수 있는 것만)

```text
주간 수익률              (portfolio_profit_loss 합산)
BUY 판단 성공률          (ai_feedback.success / 전체)
SELL 판단 성공률
목표가 도달률            (target_reached=true / 전체)
손절가 도달률            (stop_loss_reached=true / 전체)
평균 손익비              (AVG(risk_reward_ratio) where success=true)
평균 수익률              (AVG(return_rate))
최대 수익률              (MAX(return_rate))
최대 손실률              (MIN(return_rate))
confidence 구간별 성과   ([0.7, 0.8), [0.8, 0.9), [0.9, 1.0])
factor_type별 성공률    (ai_decision_factor JOIN — NEWS/DART/TECHNICAL 등)
```

> HOLD 판단 적절성, 뉴스/공시/기술 기반 성공률 등은 factor_type 통계로 대체.
> 데이터 부족 필드는 무시.

#### 3-4. 결과 저장

`portfolio_profit_loss`에 `evaluation_type='WEEKLY'`, `stock_code=NULL`, `base_date=금요일`,
`return_rate=주간 수익률`로 저장.

세부 통계는 **별도 텍스트 요약**으로 변환 후 `ai_feedback`의 신규 행으로 저장:
- `ai_decision_id`: 0 또는 sentinel (혹은 별도 컬럼 신설 검토 → 우선 0 사용)

> **DB 스키마 변경 가능성**:
> `ai_feedback.ai_decision_id`는 NOT NULL인데 WEEKLY 집계는 특정 판단에 귀속되지 않음.
> 옵션 A: 가장 최근 판단 ID에 연결 (의미 약함)
> 옵션 B: ai_feedback 스키마에 `ai_decision_id NULL 허용` + 별도 컬럼 `summary_scope VARCHAR(20)` 추가
> 옵션 C: 신규 테이블 `ai_periodic_summary` 신설
>
> → **사용자 확인 필요** (이 부분은 별도 의논)

#### 3-5. AI 판단 개선용 요약

LLM(gpt-4.1-mini)로 통계 JSON을 받아 자연어 요약 생성:
```
지난주 BUY 판단 중 confidence 0.85 이상인 케이스의 평균 수익률은 +3.2%로 높았다.
TECHNICAL factor 기반 판단은 성공률 62%, NEWS factor 기반은 45%로 차이를 보였다.
손절가 도달률은 18%로 비교적 적절했으나 목표가 도달률은 30%에 그쳐
목표가 설정이 다소 공격적이었다.
```

이 요약은 다음 주 AI 판단의 user prompt 끝에 "[지난주 피드백]" 섹션으로 포함.

#### 3-6. 신규 클래스

- `WeeklyFeedbackScheduler` (cron: `0 30 17 * * FRI`)
- `FeedbackService.evaluateWeekly(LocalDate weekEnd)` 메서드
- `AiFeedbackMapper.aggregateWeeklyStats(LocalDate from, LocalDate to)` — 집계 쿼리
- `WeeklySummaryGenerator` — OpenAI 호출하여 자연어 요약 생성
- `PeriodicSummaryStore` — 요약 저장 (테이블 선택은 위 옵션에 따름)

---

### Phase 4. MONTHLY 피드백

#### 4-1. 실행 시점

매월 1일 오전 06:00 (전월 마지막 영업일까지의 데이터 대상).

#### 4-2. 대상

전월(1일 ~ 말일) 동안의 모든 AI 판단/주문/체결/피드백 데이터.

#### 4-3. 계산 항목 (구할 수 있는 것만)

```text
월간 누적 수익률            (portfolio_profit_loss 합산)
월간 승률                   (HOLDING_END success=true 비율)
종목별 누적 수익률
모델별 성과                 (ai_prompt_log.model_name별 그룹)
confidence 구간별 성과
BUY/SELL/HOLD 판단 분포
목표가 적중률
손절가 적중률
평균 보유 기간              (체결 BUY → 청산 SELL 기간 AVG, 없으면 expected_holding_days 평균)
예상 보유 기간 적중률       (HOLDING_END 시점 success=true 비율)
주문 실패율                 (order_request status=FAILED / 전체)
리스크 검증 실패 사유 TOP N (risk_check_result.fail_reason GROUP BY)
```

> 업종별 성과, 코스피 대비 초과 성과, 최대 낙폭은 데이터가 부족하면 무시.

#### 4-4. 결과 저장

- `portfolio_profit_loss`에 `evaluation_type='MONTHLY'` 저장
- 자연어 요약은 Phase 3과 동일 방식으로 저장

#### 4-5. 전략 개선 리포트

OpenAI로 자연어 리포트 생성:
```
지난달 AI 판단은 confidence 0.85 이상에서 성공률 65%로 안정적이었다.
0.7~0.8 구간 판단은 성공률 32%로 낮아 최소 신뢰도 상향(0.8)을 권고한다.
목표가 적중률 24%로 과도하게 공격적이었고 손절가 적중률 11%는 적절했다.
NEWS factor 기반 판단보다 DART factor 기반이 19%p 높은 성공률을 보였다.
```

다음 달 AI 판단 system prompt에 "[지난달 전략 개선 제안]" 섹션으로 포함.

#### 4-6. 신규 클래스

- `MonthlyFeedbackScheduler` (cron: `0 0 6 1 * *`)
- `FeedbackService.evaluateMonthly(YearMonth month)` 메서드
- `AiFeedbackMapper.aggregateMonthlyStats(LocalDate from, LocalDate to)`
- `MonthlySummaryGenerator` (OpenAI)

---

### Phase 5. AI 프롬프트 통합

`AiDecisionPromptBuilder.buildUserPrompt` 끝에 추가:

```
## [참고: 과거 피드백 요약]
- (HOLDING_END 최근 3건 또는 종목별 최근 피드백)
- (지난주 WEEKLY 요약 1건)
- (지난달 MONTHLY 전략 개선 제안 1건)
```

DAILY는 포함하지 않음.

---

## Files / Changes

### 신규
- `scheduler/HoldingDayFeedbackScheduler.java`
- `scheduler/WeeklyFeedbackScheduler.java`
- `scheduler/MonthlyFeedbackScheduler.java`
- `feedback/service/WeeklySummaryGenerator.java`
- `feedback/service/MonthlySummaryGenerator.java`
- (조건부) `feedback/domain/AiPeriodicSummary.java` + table

### 수정
- `feedback/service/FeedbackService.java` — `evaluateHoldingDayEnd`, `evaluateWeekly`, `evaluateMonthly` 메서드
- `ai/mapper/AiFeedbackMapper.java/.xml` — `findRecentByStockCodeAndTypes`, `aggregateWeeklyStats`, `aggregateMonthlyStats`
- `ai/mapper/AiDecisionMapper.java/.xml` — `findHoldingDayMaturedDecisions`
- `market/mapper/StockPriceDailyMapper.java/.xml` — `findHighLowInRange`
- `ai/prompt/AiDecisionPromptBuilder.java` — 피드백 요약 섹션 추가
- `ai/service/AiDecisionService.java` — DAILY 제외하고 피드백 조회

### DB 스키마 변경 (사용자 승인 필요)

WEEKLY/MONTHLY 요약 저장 위치 결정 필요. **3가지 옵션 중 사용자 선택:**

**옵션 A: ai_feedback 그대로 사용**
- `ai_decision_id=0` 또는 가장 최근 판단 ID로 저장
- 장점: 스키마 변경 없음
- 단점: ai_decision_id의 의미가 약해짐, 조회 시 혼란

**옵션 B: ai_feedback 스키마 변경**
```sql
ALTER TABLE ai_feedback
  MODIFY COLUMN ai_decision_id BIGINT NULL,
  ADD COLUMN summary_scope VARCHAR(20) NULL COMMENT 'STOCK / WEEKLY / MONTHLY',
  ADD COLUMN scope_start_date DATE NULL,
  ADD COLUMN scope_end_date DATE NULL;
```

**옵션 C: 신규 테이블 `ai_periodic_summary`**
```sql
CREATE TABLE ai_periodic_summary (
  id BIGINT NOT NULL AUTO_INCREMENT,
  summary_type VARCHAR(20) NOT NULL COMMENT 'WEEKLY / MONTHLY',
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  stats_json JSON NULL,
  llm_summary TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_summary_type_period (summary_type, period_start, period_end)
) ENGINE=InnoDB COMMENT='주간/월간 AI 피드백 요약';
```

→ **추천: 옵션 C** (가장 깨끗하고 확장 용이).
→ 사용자 승인 후 진행.

---

## Test Steps

### 단위 테스트 (JUnit 5 + Mockito)

#### `FeedbackServiceTest`
1. `evaluateDecision_DAILY_BUY_목표가도달_성공`
2. `evaluateDecision_DAILY_BUY_손절가도달_실패`
3. `evaluateDecision_DAILY_SELL_역방향성공`
4. `evaluateDecision_currentPrice조회실패_basePrice대체`
5. `evaluateDecision_중복저장_INSERT_IGNORE_검증`
6. `evaluateHoldingDayEnd_기간중최고가_목표가도달_성공`
7. `evaluateHoldingDayEnd_기간중최저가_손절도달_실패`
8. `evaluateHoldingDayEnd_미만기_제외`
9. `evaluateWeekly_집계통계_정확성`
10. `evaluateMonthly_집계통계_정확성`

#### `AiFeedbackMapperTest` (테스트 DB or H2)
11. `findRecentByStockCodeAndTypes_DAILY제외`
12. `aggregateWeeklyStats_샘플데이터로_평균/MAX/MIN 검증`

#### `HoldingDayFeedbackSchedulerTest`
13. `만기도래판단_평가후재실행시_중복저장안됨`

### 통합 테스트 (`@SpringBootTest` + 테스트 DB)
14. 30일치 가짜 ai_decision + stock_price_daily seed → HoldingDay/Weekly/Monthly 풀 실행 → 결과 검증

### 수동 테스트
15. `POST /api/feedback/holding-day/run` 수동 트리거 엔드포인트 추가
16. `POST /api/feedback/weekly/run`, `POST /api/feedback/monthly/run`
17. 실행 후 DB 확인 + 다음 AI 판단 프롬프트에 요약 섹션 포함 여부 확인

---

## Risks / Assumptions

- **위험**: 만기일 계산 시 거래일 vs 캘린더일 혼동 가능
  → `created_at + expected_holding_days`는 **캘린더일** 기준으로 단순화 (추후 거래일 기준 보정 검토)
- **위험**: WEEKLY/MONTHLY 통계가 데이터 부족으로 무의미할 수 있음 (초기 1~2주)
  → 통계 항목이 모두 null/0이면 요약 생성 skip
- **위험**: OpenAI 요약 생성 비용 추가 (주 1회 + 월 1회 → 무시할 수준)
- **가정**: `stock_price_daily`에 만기까지의 일봉이 충분히 채워져 있음
  → 누락된 일봉은 무시하고 가능한 데이터로만 계산
- **가정**: 옵션 C(신규 테이블) 채택 시 DB DDL 사용자 승인 필요

---

## 진행 순서 제안

1. **Plan 13 (AI 판단 리팩토링)** 먼저 완료 (factor 다건 저장이 WEEKLY 통계의 입력이 됨)
2. **Plan 14 Phase 1 + 2** (DAILY 격리 + HoldingDay) — 가장 임팩트 큰 부분
3. **DB 스키마 옵션 확정** (옵션 A/B/C)
4. **Plan 14 Phase 3 + 4** (WEEKLY + MONTHLY)
5. **Plan 14 Phase 5** (프롬프트 통합)
6. 통합 테스트 + 수동 검증

