# Plan 18: 통합 테스트 + 수동 트리거

## Understanding

Plan 14 Test Steps 14~17번.

- **14. 통합 테스트** — 30일치 seed → HoldingDay/Weekly/Monthly 풀 실행 → 결과 검증
- **15~16. 수동 트리거 엔드포인트 추가**
  - `POST /api/feedback/holding-day/run`
  - `POST /api/feedback/weekly/run`
  - `POST /api/feedback/monthly/run`
- **17. 프롬프트 검증** — `AiDecisionPromptBuilder`가 최신 WEEKLY/MONTHLY 요약을 user prompt에 포함하는지 확인

전제: Plan 15~17 완료.

---

## Implementation Plan

### A. 수동 트리거 컨트롤러 신설 (★ 사용자 승인 필요)

**위치:** `src/main/java/com/bowon/cpm/feedback/controller/FeedbackAdminController.java`

```java
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackAdminController {

    private final FeedbackService feedbackService;
    private final HoldingDayFeedbackScheduler holdingDayScheduler;
    private final WeeklyFeedbackScheduler weeklyScheduler;
    private final MonthlyFeedbackScheduler monthlyScheduler;

    @PostMapping("/holding-day/run")
    public ApiResponse<Integer> runHoldingDay() {
        int evaluated = holdingDayScheduler.runManually();
        return ApiResponse.ok(evaluated);
    }

    @PostMapping("/weekly/run")
    public ApiResponse<Void> runWeekly(@RequestParam(required = false)
                                       @DateTimeFormat(iso = DATE) LocalDate weekEnd) {
        feedbackService.evaluateWeekly(weekEnd != null ? weekEnd : LocalDate.now());
        return ApiResponse.ok(null);
    }

    @PostMapping("/monthly/run")
    public ApiResponse<Void> runMonthly(@RequestParam(required = false) String yearMonth) {
        YearMonth ym = yearMonth != null ? YearMonth.parse(yearMonth) : YearMonth.now().minusMonths(1);
        feedbackService.evaluateMonthly(ym);
        return ApiResponse.ok(null);
    }
}
```

- 인증/권한 처리는 **로컬 운용 가정으로 생략** (사용자 확인 필요).
- 각 스케줄러에 `runManually()` public 진입 메서드 추가 필요.

---

### B. 통합 테스트 — **축소 / 조건부 실행**

**결정 (Plan 17 정책과 일치):** 운영 DB write 위험을 피하기 위해 풀 플로우 통합 테스트는 **선택 사항**으로 둔다.

#### 옵션 B-1 (기본). 통합 테스트 생략 → 단위/매퍼 테스트로 커버

- Case 14는 작성하지 않는다.
- 풀 플로우 검증은 수동 트리거(Case 15~16)로 로컬에서 직접 실행 후 DB 확인으로 대체.
- CI는 Plan 16(단위) + Plan 17(매퍼) + Case 15/16/17만 통과 기준으로 사용.

#### 옵션 B-2 (조건부). 축소된 통합 테스트 1건만 유지

- seed 데이터 최소화 (`stock_code='TEST_INT_001'` 1종목, ai_decision 2건)
- 모든 테스트 메서드에 `@Transactional` 강제 + 컨트롤러/서비스 트랜잭션 propagation을 미리 확인하여 REQUIRES_NEW 없음을 검증
- 그래도 잔여 데이터 가능성 있으므로 `@Tag("integration")`으로 분리하여 기본 `./gradlew test`에서 제외, `./gradlew integrationTest`로만 명시 실행

**추천: 옵션 B-1.** 위험 회피.

### Case 14. (옵션 B-1 채택 시 — 생략)

→ 작성하지 않음. 로컬 수동 검증으로 대체.

### Case 14. (옵션 B-2 채택 시 — 축소판)

```java
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional   // ★ 트랜잭션 propagation REQUIRED 가정 시 자동 롤백
@Tag("integration")
class FeedbackFullFlowIntegrationTest {
    // seed: TEST_INT_001 종목 1개 + ai_decision 2건 + stock_price_daily 7일치
    // 1) /holding-day/run 호출 → ai_feedback HOLDING_END 1건 검증
    // 2) /weekly/run 호출 → ai_periodic_summary WEEKLY 1건 검증
}
```

**선결 조건 (옵션 B-2 사용 시):**
- `FeedbackService.evaluateHoldingDayEnd` / `evaluateWeekly` / `evaluateMonthly`의 `@Transactional` propagation을 코드 확인 → `REQUIRES_NEW` 발견 시 옵션 B-1로 강제 전환

`src/test/java/com/bowon/cpm/feedback/FeedbackFullFlowIntegrationTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FeedbackFullFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiPeriodicSummaryMapper periodicSummaryMapper;
    @Autowired AiFeedbackMapper aiFeedbackMapper;

    @MockBean OpenAiDecisionClient openAiClient; // LLM 호출 차단
    @MockBean BrokerClient brokerClient;          // 현재가 조회 차단
}
```

---

### Case 14 상세 시나리오 (옵션 B-2 채택 시 참고)

**Given (seed)**
- `stock_master`: `005930`
- `stock_price_daily`: 2026-05-01 ~ 2026-05-30 (30 영업일분 OHLCV)
  - 의도된 분포: high가 target을 넘는 구간, low가 stopLoss를 깨는 구간 혼합
- `ai_decision` 10건:
  - BUY 6건 / SELL 3건 / HOLD 1건
  - createdAt: 2026-05-01 ~ 2026-05-25 분산
  - expectedHoldingDays: 3, 5, 7, 10, 14, 20 분포
  - confidence: [0.72, 0.81, 0.85, 0.91, ...] 다양

**Mock**
- `brokerClient.getCurrentPrice("005930")` → 11200 (고정)
- `openAiClient.createDecision(...)` → 더미 요약 텍스트 반환

**When**
```java
// 1) 만기 도래 판단 평가
mvc.perform(post("/api/feedback/holding-day/run")).andExpect(status().isOk());

// 2) 주간 집계
mvc.perform(post("/api/feedback/weekly/run")
        .param("weekEnd", "2026-05-29")).andExpect(status().isOk());

// 3) 월간 집계
mvc.perform(post("/api/feedback/monthly/run")
        .param("yearMonth", "2026-05")).andExpect(status().isOk());
```

**Then**
- `ai_feedback` 테이블:
  - `HOLDING_END` 행이 만기 도래 판단 수만큼 생성
  - 각 행의 `success`, `target_reached`, `stop_loss_reached`, `return_rate`가 seed 기반 기대값과 일치
- `portfolio_profit_loss`:
  - WEEKLY 행 1건 (`base_date=2026-05-29`)
  - MONTHLY 행 1건 (`base_date=2026-05-31` 또는 정책 일자)
- `ai_periodic_summary`:
  - `summary_type=WEEKLY` 1건, `period_start=2026-05-25`, `period_end=2026-05-29`
  - `summary_type=MONTHLY` 1건, `period_start=2026-05-01`, `period_end=2026-05-31`
  - 각 행의 `stats_json` 파싱 가능, `llm_summary` 비어있지 않음
- 재실행해도 행 수 동일 (멱등성)

---

### Case 17. 프롬프트 통합 검증

**선결 확인:** Plan 14 Phase 5 — `AiDecisionPromptBuilder`가 `AiPeriodicSummaryMapper`에서 최신 WEEKLY/MONTHLY를 읽어 user prompt에 포함하는지.

**테스트:** `AiDecisionPromptBuilderTest` — `@SpringBootTest + @Transactional`로 작성. summary 1~2건만 insert하므로 자동 롤백으로 안전.

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional   // ← 1~2건 insert → 자동 롤백
class AiDecisionPromptBuilderTest {
    @Autowired AiDecisionPromptBuilder builder;
    @Autowired AiPeriodicSummaryMapper periodicMapper;

    @Test
    void user_prompt_에_최신_WEEKLY와_MONTHLY_요약이_포함된다() {
        // Given: 최신 WEEKLY/MONTHLY summary 1건씩 seed
        periodicMapper.insertIgnore(weeklySummary("지난주 BUY 평균 +2.4%..."));
        periodicMapper.insertIgnore(monthlySummary("지난달 전략 개선 제안..."));

        // When
        String prompt = builder.buildUserPrompt(sampleInputJson);

        // Then
        assertThat(prompt).contains("지난주 BUY 평균 +2.4%");
        assertThat(prompt).contains("지난달 전략 개선 제안");
        // DAILY 요약은 포함되지 않아야 함
        assertThat(prompt).doesNotContain("DAILY");
    }
}
```

---

### Case 15~16. 수동 트리거 자체 검증

`FeedbackAdminControllerTest` (`@WebMvcTest` + `@MockBean` 의존성)

- 200 응답 + 위임 호출 1회 검증
- 잘못된 날짜 포맷 → 400

---

## Files / Changes

### 신규
- `src/main/java/com/bowon/cpm/feedback/controller/FeedbackAdminController.java`
- 각 스케줄러에 `runManually()` 메서드 (HoldingDay/Weekly/Monthly)
- `src/test/java/com/bowon/cpm/feedback/controller/FeedbackAdminControllerTest.java` (`@WebMvcTest`, DB 무관)
- `src/test/java/com/bowon/cpm/ai/prompt/AiDecisionPromptBuilderTest.java` (`@SpringBootTest + @Transactional`)
- (옵션 B-2 채택 시만) `src/test/java/com/bowon/cpm/feedback/FeedbackFullFlowIntegrationTest.java`

### 수정
- `HoldingDayFeedbackScheduler`, `WeeklyFeedbackScheduler`, `MonthlyFeedbackScheduler` — 수동 진입 메서드 노출

### DB
- 변경 없음

---

## Test Steps

```bash
./gradlew test --tests "com.bowon.cpm.feedback.controller.FeedbackAdminControllerTest"
./gradlew test --tests "com.bowon.cpm.ai.prompt.AiDecisionPromptBuilderTest"
# (옵션 B-2 채택 시만)
./gradlew test --tests "com.bowon.cpm.feedback.FeedbackFullFlowIntegrationTest"
```

수동:
```bash
curl -X POST http://localhost:8080/api/feedback/holding-day/run
curl -X POST "http://localhost:8080/api/feedback/weekly/run?weekEnd=2026-05-29"
curl -X POST "http://localhost:8080/api/feedback/monthly/run?yearMonth=2026-05"
```

→ 응답 200 + DB 확인.

---

## Risks / Assumptions

- **가정**: Plan 14 Phase 5(프롬프트 통합)가 실제로 구현되어 `AiPeriodicSummary`를 읽도록 되어 있음. 아니면 Case 17은 먼저 구현 후 테스트.
- **가정**: 스케줄러 클래스에 `runManually()` 또는 동등 public 진입점을 추가해도 무방.
- **위험**: `@SpringBootTest`는 전체 컨텍스트를 띄우므로 외부 API(KIS/DART/Naver/OpenAI) 호출이 실제로 일어나지 않도록 `@MockBean` 또는 test 프로파일의 dummy URL로 차단 필수.
- **완화**: 풀 플로우 통합 테스트(Case 14)는 **옵션 B-1 채택으로 생략 권장** → 운영 DB write 위험 제거. 풀 검증은 수동 트리거 + 로컬 DB 확인으로 대체.
- **위험 (옵션 B-2 채택 시)**: 서비스 내부 `REQUIRES_NEW`가 있으면 `@Transactional` 롤백 무력화 → 사전 코드 확인 필수.
- **위험**: 수동 트리거 컨트롤러를 인증 없이 공개하면 운영 환경에서 악용 가능 → 운영 배포 시 `@Profile("local")` 또는 인터셉터로 보호 필요.

---

## 진행 순서 제안

1. Plan 15~17 완료 선행
2. 사용자 승인: 수동 트리거 컨트롤러 신설 OK / 인증 정책
3. 스케줄러에 `runManually()` 추가
4. `FeedbackAdminController` 작성
5. Case 15~16 (Controller 테스트) → Case 14 (통합) → Case 17 (프롬프트)
6. 수동 검증







