# Plan 17: Mapper / Scheduler 테스트

## Understanding

Plan 14 Test Steps 11~13번.

- **11. `findRecentByStockCodeAndTypes_DAILY제외`** — Plan 14 Phase 1에서 추가된 Mapper 메서드 검증
- **12. `aggregateWeeklyStats_샘플데이터로_평균/MAX/MIN 검증`** — 집계 SQL 정확성
- **13. `만기도래판단_평가후재실행시_중복저장안됨`** — `HoldingDayFeedbackScheduler` 멱등성

DB가 필요한 테스트. **운영 `cpm` DB** 사용 + `@Transactional` 자동 롤백 + 테스트 데이터 marker(`stock_code='TEST_*'`).

---

## Implementation Plan

### A. `AiFeedbackMapperTest` (`@MybatisTest`)

`src/test/java/com/bowon/cpm/feedback/mapper/AiFeedbackMapperTest.java`

```java
@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 운영 MySQL 사용
// @MybatisTest는 기본 @Transactional 포함 → 메서드 끝나면 자동 롤백
class AiFeedbackMapperTest {
    @Autowired AiFeedbackMapper mapper;
    @Autowired JdbcTemplate jdbc;
}
```

> seed는 JdbcTemplate으로 같은 트랜잭션 내부에 INSERT → 메서드 종료 시 함께 롤백되므로 marker 없이도 안전.
> `@Sql` 사용 시에는 `@SqlConfig(transactionMode = INFERRED)` 명시.

---

### Case 11. `findRecentByStockCodeAndTypes_DAILY제외`

**선결 확인**
- `AiFeedbackMapper.findRecentByStockCodeAndTypes(stockCode, types, limit)`가 실제로 구현되어 있는지 확인.
- 없으면 Plan 14 Phase 1 미구현이므로 **별도 보고 후 구현**.

**Given (JdbcTemplate로 seed)**
- `ai_feedback` 5건 삽입:
  - DAILY × 2
  - WEEKLY × 1
  - MONTHLY × 1
  - HOLDING_END × 1
- 모두 `stock_code='005930'`

**When**
```java
List<AiFeedback> result = mapper.findRecentByStockCodeAndTypes(
    "005930", List.of("WEEKLY", "MONTHLY", "HOLDING_END"), 10);
```

**Then**
- `result.size() == 3`
- `result.stream().noneMatch(f -> "DAILY".equals(f.evaluationType()))`

---

### Case 12. `aggregateWeeklyStats_샘플데이터로_평균/MAX/MIN`

**선결 확인**
- `AiFeedbackMapper.aggregateStatsBetween(from, to)` 또는 동등 메서드 존재 여부.
- 없으면 Plan 14 Phase 3 미구현 → 보고 후 구현.

**Given**
- 주간 범위 (2026-05-25 ~ 2026-05-29) 내 `ai_feedback` 5건:
  - returnRate: 5.0, -2.0, 8.0, 1.0, -4.0
  - success: T, F, T, T, F
  - target_reached: T, F, T, F, F
  - stop_loss_reached: F, T, F, F, T

**When**
```java
WeeklyStats stats = mapper.aggregateStatsBetween(from, to);
```

**Then**
- `count == 5`
- `successCount == 3`, `winRate ≈ 0.6`
- `avgReturnRate ≈ 1.6`, `maxReturnRate == 8.0`, `minReturnRate == -4.0`
- `targetReachedCount == 2`, `stopLossReachedCount == 2`

---

### B. `HoldingDayFeedbackSchedulerTest` — **Mockito 단위 테스트로 전환**

`src/test/java/com/bowon/cpm/scheduler/HoldingDayFeedbackSchedulerTest.java`

**결정:** `@Sql(AFTER_TEST_METHOD)` cleanup이 운영 DB에 잔여 데이터 위험을 남기므로, 스케줄러는 **순수 Mockito 단위 테스트**로만 검증한다. 실제 멱등성은 Mapper 레벨(`insertIgnore` UK 차단)에서 별도 검증.

```java
@ExtendWith(MockitoExtension.class)
class HoldingDayFeedbackSchedulerTest {
    @Mock FeedbackService feedbackService;
    @Mock AiDecisionMapper aiDecisionMapper;
    @InjectMocks HoldingDayFeedbackScheduler scheduler;
}
```

> 스케줄러는 "만기 도래 판단 조회 → 각각 위임"만 하므로 단위 테스트로 충분.
> 실제 DB 멱등성 검증은 Case 14(Mapper 추가 케이스)로 흡수.

---

### Case 13. (단위 테스트) `만기도래판단_조회후_FeedbackService에_위임`

**Given**
- `aiDecisionMapper.findHoldingDayMaturedDecisions(today)` → `List.of(decision1, decision2)` mock
- 동일 호출 반복 시 동일 결과 (실제 DB의 "평가 완료 제외" 로직은 Case 11/12에서 검증)

**When**
```java
scheduler.run(); // 또는 evaluateMatured()
```

**Then**
- `feedbackService.evaluateHoldingDayEnd(decision1.getId())` 1회 호출
- `feedbackService.evaluateHoldingDayEnd(decision2.getId())` 1회 호출
- 예외 없이 정상 종료

### Case 13-b. (Mapper 단위 검증) `중복저장방지_UK차단` → Case 11/12에 추가

`AiFeedbackMapperTest`에 케이스 추가:

**Given**
- 같은 `(ai_decision_id, evaluation_type='HOLDING_END')` 조합으로 2번 `insertIgnore` 호출

**Then**
- DB 행 수는 1 (UK 차단 검증)
- 2번째 호출에서 예외 없음

→ `@MybatisTest + @Transactional`로 자동 롤백되므로 안전.

---

## Files / Changes

### 신규
- `src/test/java/com/bowon/cpm/feedback/mapper/AiFeedbackMapperTest.java`
- `src/test/java/com/bowon/cpm/scheduler/HoldingDayFeedbackSchedulerTest.java` (Mockito 단위)

### 수정 (선결 구현 시)
- `AiFeedbackMapper.java/.xml` — `findRecentByStockCodeAndTypes`, `aggregateStatsBetween` (미구현 시)
- `AiDecisionMapper.java/.xml` — `findHoldingDayMaturedDecisions` (미구현 시)

### DB
- 변경 없음 (DDL 동일)
- **`cleanup-feedback.sql` 불필요** (스케줄러 통합 테스트 제거로 운영 DB write 없음)

---

## Test Steps

```bash
./gradlew test --tests "com.bowon.cpm.feedback.mapper.AiFeedbackMapperTest"
./gradlew test --tests "com.bowon.cpm.scheduler.HoldingDayFeedbackSchedulerTest"
```

전부 GREEN.

---

## Risks / Assumptions

- **가정**: 운영 `cpm` DB에 접속 가능하고, `@MybatisTest` 기본 `@Transactional`로 자동 롤백 격리 가능.
- **위험**: `@MybatisTest`는 기본적으로 H2를 시도하므로 `@AutoConfigureTestDatabase(replace=NONE)` 필수.
- **위험**: Plan 14에서 명시한 메서드가 실제로는 다른 이름으로 구현됐을 수 있음 → 코드 확인 후 메서드명 매핑.
- **완화**: 스케줄러 통합 테스트 제거로 운영 DB write 위험 없음. 멱등성은 Mapper UK 차단으로만 검증 (Case 13-b).
- **트레이드오프**: 스케줄러 ↔ FeedbackService ↔ Mapper 전 구간 통합 검증은 빠짐. Plan 18 통합 테스트에서 일부 커버.

---

## 진행 순서 제안

1. Plan 15, 16 완료 선행
2. 선결 메서드(`findRecentByStockCodeAndTypes` 등) 구현 상태 확인 → 미구현 시 보고
3. Case 11, 12 작성 (Mapper)
4. Case 13 작성 (Scheduler 멱등성)
5. → Plan 18 진입

