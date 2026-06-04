# Plan 13: AI 판단 생성 로직 리팩토링 (P1 + P2 + P3)

## Understanding

`AiDecisionService.generateDecision`은 데이터 수집, AI 응답 활용, 트랜잭션 경계,
재검토 처리 측면에서 여러 결함이 있어 신뢰할 수 있는 매매 판단을 만들지 못함.
사용자 승인 사항:

- **P1, P2, P3 전부 진행**
- **재검토 불일치 시 `decision` 컬럼은 그대로 BUY로 유지** (`decision_status`만 `HOLD_BY_REVIEW`로 변경 — 현재 로직 유지)
  - 이유: 추후 Admin 화면에서 1차 BUY vs 재검토 HOLD 불일치 케이스를 추적·분석하기 위함
- 일봉 조회: **거래일 기준 최근 ~ 캘린더 60일 (약 2개월)**
- 뉴스 조회: **최근 7일 (필터 추가)**

DB 스키마 변경은 없음. 기존 컬럼을 더 풍부하게 활용.

---

## Implementation Plan

### P1. 정합성 / 안정성

#### P1-1. `@Transactional` 분리 (외부 API 호출을 트랜잭션 밖으로)

현재 `generateDecision` 전체가 `@Transactional` → OpenAI(수십초), KIS 호출까지 DB 커넥션 점유.
backend.instructions.md 규칙 위반.

**변경 후 구조:**

```
generateDecision(stockCode)  // 트랜잭션 없음
  ├─ collectData(stockCode)               // 외부 API + DB 조회
  ├─ openAiClient.createDecision(...)     // OpenAI 1차
  ├─ openAiClient.createDecision(review)  // OpenAI 재검토 (조건부)
  └─ persistDecision(...)                 // @Transactional, DB INSERT만
```

- `persistDecision`은 별도 메서드(@Transactional)로 분리해 `promptLog → rawResponse → decision → factors` 한 트랜잭션에 묶음.
- `ExternalApiCallLog` 저장은 finally 블록에서 별도 트랜잭션(REQUIRES_NEW)으로 처리.

#### P1-2. `parse_error`를 DB 컬럼에 저장

현재 `updateParseError`는 로그만 남기고 끝.
`ai_decision_raw_response.parse_error` 컬럼 UPDATE 쿼리 추가.

**Mapper 추가:**
```xml
<update id="updateParseError">
  UPDATE ai_decision_raw_response
  SET parse_error = #{parseError}, is_parsed = 0
  WHERE id = #{id}
</update>
```

#### P1-3. `stockName` 강제 주입

AI 응답의 `stockName`은 "005930" 같은 값이 그대로 들어오는 케이스가 빈번.
`stock_master`에서 조회한 정식 종목명을 강제 사용.

```java
String stockName = stockService.findByStockCode(stockCode)
        .map(StockMaster::getStockName)
        .orElse(stockCode);

// ai_decision 저장 시
.stockName(stockName)  // parsed.stockName() 무시
```

#### P1-4. 재검토 결과 처리 (현재 로직 유지)

- `decision_status`만 `HOLD_BY_REVIEW`로 변경
- `decision` 컬럼은 `BUY` 그대로 (Admin 분석용)
- 현재 `findPendingBuyDecisions`가 `status='CREATED'`로 필터링하므로 잘못된 주문 위험 없음 (확인 완료)

---

### P2. 데이터 품질

#### P2-1. 일봉: 최근 2개월(거래일 기준)

```java
List<StockPriceDaily> dailyPrices = stockPriceDailyMapper.findByStockCodeAndDateRange(
        stockCode, LocalDate.now().minusDays(60), LocalDate.now());
```

(현재 mapper는 그대로 사용 가능. 캘린더 60일 ≈ 거래일 40여 개)

#### P2-2. 뉴스 조회에 날짜 필터 (최근 7일)

**Mapper 메서드 추가:**
```xml
<select id="findByStockCodeAndPublishedAfter" resultType="...">
  SELECT ...
  FROM stock_news
  WHERE stock_code = #{stockCode}
    AND published_at >= #{since}
  ORDER BY published_at DESC, collected_at DESC
  LIMIT #{limit}
</select>
```

```java
// Service
List<StockNews> newsList = stockNewsMapper.findByStockCodeAndPublishedAfter(
        stockCode, LocalDateTime.now().minusDays(7), 10);
```

#### P2-3. 주요이벤트 조회에 날짜 필터 (최근 3개월)

```xml
<select id="findByStockCodeAndDateAfter" resultType="...">
  SELECT ... FROM dart_major_event
  WHERE stock_code = #{stockCode}
    AND event_date >= #{since}
  ORDER BY event_date DESC
  LIMIT #{limit}
</select>
```

#### P2-4. 기술적 지표 추가 (`stock_indicator_daily` 최신 1건)

프롬프트에 RSI, MACD, MA5/20/60, 볼린저밴드, 변동성 포함.

**의존성 추가:**
- `StockIndicatorDailyMapper.findLatestByStockCode(stockCode)` 메서드 추가
- (`stock_indicator_daily` 테이블은 이미 존재. 데이터 채우는 스케줄러는 별도 이슈)

#### P2-5. KIS 실시간 현재가 사용

현재 프롬프트의 "현재가"는 일봉 종가(전일).
장중 판단에는 부적절.

```java
BigDecimal realtimePrice = null;
try {
    realtimePrice = brokerClient.getCurrentPrice(stockCode).getCurrentPrice();
} catch (Exception e) {
    log.warn("[AI] 실시간 현재가 조회 실패, 일봉 종가 사용: {}", e.getMessage());
}
```

프롬프트의 "현재가" 라벨로 표시하고, AI 응답의 `currentPrice`와 비교용으로도 활용.

---

### P3. AI 응답 품질

#### P3-1. OpenAI JSON Schema 확장

**현재 응답 필드(유지):**
```
stockCode, stockName, decision, confidence,
currentPrice, targetPrice, stopLossPrice,
expectedReturnRate, expectedLossRate, riskRewardRatio,
recommendedPortfolioWeight, expectedHoldingDays,
riskLevel, reason
```

**추가:**
```json
{
  "analysis": {
    "technicalAnalysis": "...",
    "newsAnalysis": "...",
    "disclosureAnalysis": "...",
    "fundamentalAnalysis": "...",
    "supplyDemandAnalysis": "..."
  },
  "factors": [
    {
      "type": "TECHNICAL|NEWS|DART|FUNDAMENTAL|SUPPLY_DEMAND",
      "direction": "POSITIVE|NEGATIVE|NEUTRAL",
      "score": 0.0~1.0,
      "summary": "한 줄 요약"
    }
  ]
}
```

#### P3-2. `AiTradeDecisionJson` 확장

```java
public record AiTradeDecisionJson(
    // ...기존 필드...
    String reason,
    Analysis analysis,
    List<FactorJson> factors
) {
    public record Analysis(
        String technicalAnalysis, String newsAnalysis,
        String disclosureAnalysis, String fundamentalAnalysis,
        String supplyDemandAnalysis
    ) {}

    public record FactorJson(
        String type, String direction,
        BigDecimal score, String summary
    ) {}
}
```

#### P3-3. `buildFactors` 다중 factor 저장

```java
private List<AiDecisionFactor> buildFactors(Long decisionId, AiTradeDecisionJson parsed) {
    List<AiDecisionFactor> factors = new ArrayList<>();

    if (parsed.factors() != null) {
        for (var f : parsed.factors()) {
            factors.add(AiDecisionFactor.builder()
                .aiDecisionId(decisionId)
                .factorType(f.type())
                .factorDirection(f.direction())
                .factorScore(f.score())
                .factorSummary(f.summary())
                .build());
        }
    }

    // fallback: factors 비어있고 reason은 있을 때 기존 로직
    if (factors.isEmpty() && parsed.reason() != null && !parsed.reason().isBlank()) {
        // 현재 로직 유지
    }
    return factors;
}
```

#### P3-4. System Prompt 강화

추가 지침:
```
- 판단 근거를 reason 한 줄과 별도로 analysis 객체로 영역별로 분리해 제시하라.
- factors 배열에는 판단에 영향을 준 요인을 2~5개로 분리해 제시하라.
  각 factor는 type(TECHNICAL|NEWS|DART|FUNDAMENTAL|SUPPLY_DEMAND),
  direction(POSITIVE|NEGATIVE|NEUTRAL), score(0~1), summary로 구성한다.
- 계좌 정보(totalAsset, availableCash)가 제공되면
  recommendedPortfolioWeight를 책정할 때 반드시 참고하라.
  예수금을 초과하는 비중을 제시하지 마라.
- 데이터가 부족한 영역은 해당 analysis 필드를 null로 두고 무리해서 추측하지 마라.
```

---

## Files / Changes

| 파일 | 변경 |
|------|------|
| `AiDecisionService.java` | 트랜잭션 분리, stockName 주입, parse_error UPDATE 호출, 다중 factor 처리 |
| `OpenAiDecisionClient.java` | JSON Schema에 analysis + factors 추가 |
| `AiTradeDecisionJson.java` | Analysis, FactorJson 중첩 record 추가 |
| `AiDecisionParser.java` | (Jackson 자동, 변경 거의 없음) |
| `AiDecisionPromptBuilder.java` | system prompt 강화, indicator/실시간가 user prompt 포함 |
| `AiDecisionRawResponseMapper.xml` | `updateParseError` UPDATE 추가 |
| `StockNewsMapper.java/.xml` | `findByStockCodeAndPublishedAfter` 추가 |
| `DartMajorEventMapper.java/.xml` | `findByStockCodeAndDateAfter` 추가 |
| `StockIndicatorDailyMapper.java/.xml` | `findLatestByStockCode` 추가 (신규 또는 기존 활용) |

---

## Test Steps

### 단위 테스트 (JUnit 5 + Mockito)

1. **`AiDecisionServiceTest`**
   - `generateDecision_정상_BUY` — Mock으로 OpenAI 응답 주입 → ai_decision/factor 저장 검증
   - `generateDecision_파싱실패` — 비정상 JSON 응답 → parse_error UPDATE 호출 검증
   - `generateDecision_고신뢰BUY재검토_불일치` — 1차 BUY/0.85, 재검토 HOLD → decision_status=HOLD_BY_REVIEW
   - `generateDecision_고신뢰BUY재검토_일치` → 상태 변경 없음
   - `generateDecision_stockName주입` — AI가 "005930" 반환해도 DB엔 "삼성전자" 저장
   - `generateDecision_factors다건저장` — 4개 factor 응답 → factor 테이블 4건 저장

2. **`AiDecisionParserTest`**
   - analysis 객체 + factors 배열 포함 응답 파싱 성공
   - factors 누락 응답도 정상 파싱 (null)

3. **`OpenAiDecisionClientTest`** (선택, schema 검증)
   - 새 schema가 빌드되는지 단위 검증

### 통합 테스트

4. **수동 통합 테스트**
   - `POST /api/ai/decisions?stockCode=005930` 호출
   - DB에 ai_prompt_log, raw_response, decision, factor(다건) 저장 확인
   - 응답의 analysis 필드 모두 채워졌는지 확인
   - parse_error 케이스: 일부러 잘못된 모델로 호출하여 에러 처리 흐름 확인

---

## Risks / Assumptions

- **위험**: P3 JSON Schema 확장 시 OpenAI Structured Output strict 모드에서 schema 검증 실패 가능
  → 신규 필드는 모두 nullable(required에 미포함)로 설정
- **위험**: 출력 토큰 증가로 비용 약 3~4배 (gpt-4.1-mini 기준 종목당 0.5~1원 증가)
  → 사용자 승인됨 (P1+P2+P3 진행)
- **가정**: `stock_indicator_daily` 데이터가 채워져 있어야 의미 있음
  → 비어있어도 graceful 처리 (조회 실패 시 프롬프트에서 생략)
- **가정**: `@Transactional` 분리 후 ID 채번(useGeneratedKeys)이 정상 동작 (MyBatis 기본 동작)

