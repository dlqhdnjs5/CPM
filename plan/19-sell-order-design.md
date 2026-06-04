# Plan 19: 매도(SELL) 프로세스 도입

> 참고: `plan/PROCESS-AI-ORDER.md` (현재 BUY 전용 흐름 + 갭 G1~G7)

---

## Understanding

현재 시스템은 매수 한 방향만 자동화되어 있다. AI가 SELL을 만들어도 픽업되지 않고, 보유 종목이 목표가/손절가에 도달해도 자동 매도가 없다. **매도 프로세스 전체를 추가**해야 한다.

핵심 설계 원칙 (사용자 결정 반영):
1. **AI SELL 수량 정책**: `recommendedPortfolioWeight` 비례 사용 (별도 필드 추가 X — AI 부담·검증 어려움 회피)
   - `weight = 0` → 전량 매도
   - `weight > 0` → 목표 비중까지 줄이기 (= 현재 보유에서 일부 매도)
2. **익절 정책**: 분할 익절 (1차 50%, 잔여는 손절선까지 유지)
3. **손절 정책**: 장중 즉시 전량 매도 (시장가)
4. **만기 도래**: 피드백만 (강제 청산 안 함, 현재 동작 유지)

설계 가독성 우선. 매수와 매도 책임을 **클래스 단위로 명확히 분리**한다.

---

## Implementation Plan

### A. 모듈 분리 (가독성 핵심)

```
order/
├── policy/
│   ├── OrderPolicyEngine.java      ← 기존, BUY 전용으로 명시 (이름 유지)
│   └── SellOrderPolicyEngine.java  ← 신설, SELL 전용
├── service/
│   ├── OrderService.java
│   │     ├─ placeOrder(decisionId)        ← 진입점, BUY/SELL 분기
│   │     ├─ placeBuyOrder(decision, ...)  ← 기존 로직 추출 (private)
│   │     └─ placeSellOrder(decision, ...) ← 신설 (private)
│   └── ExecutionSyncService.java   ← SELL 체결 시 portfolio_profit_loss REALIZED 추가
├── executor/
│   └── OrderExecutor.java          ← 변경 없음 (이미 BUY/SELL 분기 처리)
└── trigger/                         ← 신설 패키지
    └── SellTrigger.java             ← enum: AI_DECISION / TARGET_HIT / STOP_LOSS_HIT
```

```
risk/
├── rule/
│   ├── RiskManager.java            ← 기존 (BUY 위주 룰)
│   └── SellRiskManager.java        ← 신설, SELL 전용 (보유 여부/수량 검증)
└── service/
    └── RiskService.java            ← 분기 (BUY → RiskManager / SELL → SellRiskManager)

scheduler/
├── OrderExecutionScheduler.java    ← 변경: BUY/SELL 둘 다 픽업
└── TargetStopMonitorScheduler.java ← 신설, 장중 1분 주기 익절/손절 감시
```

### B. SellOrderPolicyEngine 설계 (단순 명료)

```java
@Component
public class SellOrderPolicyEngine {

    public record SellCalculation(int quantity, BigDecimal unitPrice) {
        public boolean isOrderable() { return quantity >= 1; }
    }

    /**
     * SELL 수량 계산
     *
     * trigger 별 정책:
     *   AI_DECISION:   weight==0 → 전량 / weight>0 → 목표비중까지 축소
     *   TARGET_HIT:    분할 1차 → 보유의 50% (소수점 floor, 최소 1주)
     *                  분할 2차(이전 익절 이력 있음) → 잔여 전량
     *   STOP_LOSS_HIT: 전량
     *
     * 1주 미만이면 0 반환 (주문 불가)
     */
    public SellCalculation calculate(
        AiDecision decision,        // weight / currentPrice 참조
        PortfolioPosition position, // 현재 보유 수량/평균가
        BigDecimal currentPrice,    // 실시간 현재가
        BigDecimal totalAsset,
        SellTrigger trigger,
        boolean hasPreviousPartialSell // 같은 ai_decision_id로 익절 1차 이력 존재 여부
    );
}
```

**계산식 (트리거별):**

| Trigger | 수량 산출 |
|---------|----------|
| `AI_DECISION` + weight=0 | `quantity = position.quantity` (전량) |
| `AI_DECISION` + weight>0 | `targetQty = floor(totalAsset × weight / currentPrice)`<br>`sellQty = max(0, position.quantity - targetQty)` |
| `TARGET_HIT` + 1차 | `sellQty = max(1, floor(position.quantity / 2))` |
| `TARGET_HIT` + 2차 | `sellQty = position.quantity` (잔여 전량) |
| `STOP_LOSS_HIT` | `sellQty = position.quantity` (즉시 전량) |

**1주 보유 시:** 분할 불가 → 즉시 전량.

### C. SellRiskManager 검증 룰

```java
public String check(AiDecision decision, PortfolioPosition position, int requestedQty) {
    // 1. 보유 수량 존재
    if (position == null || position.getQuantity() <= 0)
        return "보유 수량 없음";

    // 2. 요청 수량 > 보유 수량 차단
    if (requestedQty > position.getQuantity())
        return "보유 수량 초과 매도";

    // 3. 최소 매도 수량 1
    if (requestedQty < 1)
        return "매도 수량 0";

    // 4. (선택) AI confidence 최소치
    if (decision.getDecision().equals("SELL")
            && decision.getConfidence() != null
            && decision.getConfidence().compareTo(MIN_SELL_CONFIDENCE) < 0)
        return "AI 신뢰도 부족 (SELL)";

    return null;
}
```

`risk_policy_config`에 신규 정책 row 추가:
- `MIN_SELL_CONFIDENCE` (예: 0.6)
- `TARGET_HIT_PARTIAL_RATIO` (예: 0.5) — TargetStopMonitor용

### D. OrderService.placeOrder 리팩토링

```java
@Transactional
public OrderRequest placeOrder(Long aiDecisionId) {
    AiDecision decision = ... ;
    
    // 공통 가드: HOLD / HOLD_BY_REVIEW / 리스크 통과 / idempotency
    validateCommon(decision);
    String idempotencyKey = buildIdempotencyKey(decision);
    if (existsByKey(idempotencyKey)) return existing;

    return switch (decision.getDecision()) {
        case "BUY"  -> placeBuyOrder(decision, idempotencyKey);
        case "SELL" -> placeSellOrder(decision, idempotencyKey, SellTrigger.AI_DECISION);
        default     -> throw new IllegalStateException("미지원: " + decision.getDecision());
    };
}

private OrderRequest placeSellOrder(AiDecision d, String key, SellTrigger trigger) {
    // 1. 보유 포지션 조회 (없으면 풀러백 안 함, 명확히 예외)
    PortfolioPosition pos = portfolioPositionMapper
        .findByAccountNoAndStockCode(accountNo, d.getStockCode())
        .orElseThrow(() -> new IllegalStateException("보유 종목 없음: " + d.getStockCode()));

    // 2. 현재가 + 잔고 (잔고는 SELL에 필수 아님 - 실패해도 진행)
    BigDecimal currentPrice = brokerClient.getCurrentPrice(d.getStockCode()).getCurrentPrice();
    BigDecimal totalAsset = safeTotalAsset();

    // 3. 익절 1차 이력 확인 (orderRequestMapper.countTodaySell(decisionId, TARGET_HIT))
    boolean hasPartial = orderRequestMapper.existsPartialSellForDecision(d.getId());

    // 4. 수량 계산
    SellCalculation calc = sellPolicyEngine.calculate(d, pos, currentPrice, totalAsset, trigger, hasPartial);
    if (!calc.isOrderable())
        throw new IllegalStateException("매도 수량 0");

    // 5. SELL 리스크 검증
    String fail = sellRiskManager.check(d, pos, calc.quantity());
    if (fail != null) throw new IllegalStateException("SELL 리스크 실패: " + fail);

    // 6. order_request INSERT (orderSide=SELL, requestReason에 trigger 포함)
    OrderRequest req = OrderRequest.builder()
        .aiDecisionId(d.getId())
        .accountNo(accountNo)
        .brokerType("KIS")
        .stockCode(d.getStockCode())
        .orderSide("SELL")
        .orderType("MARKET")
        .orderPrice(currentPrice)
        .orderQuantity(calc.quantity())
        .orderAmount(currentPrice.multiply(BigDecimal.valueOf(calc.quantity())))
        .orderStatus("READY")
        .idempotencyKey(key)
        .requestReason("SELL[" + trigger + "]: " + (d.getReason() != null ? d.getReason() : ""))
        .build();
    orderRequestMapper.insert(req);
    saveStatusHistory(req.getId(), null, "READY", "매도 요청 생성");

    // 7. 실행
    orderExecutor.execute(req);
    return req;
}
```

`idempotencyKey`에 trigger 포함:
```
{acc}:{code}:{decisionId}:SELL:{TARGET_HIT|STOP_LOSS_HIT|AI_DECISION}:{yyyyMMddHHmm}
```
→ 같은 종목 분할 익절 2회는 trigger가 같아도 시각이 달라 구분됨. 하지만 1분 안에 두 번 처리되면 충돌 가능 → trigger 안에 `차수` 포함 검토(예: `TARGET_HIT_1`, `TARGET_HIT_2`).

### E. OrderExecutionScheduler 변경

```java
// 기존 findPendingBuyDecisions → findPendingDecisions (BUY/SELL 양쪽)
List<AiDecision> pending = aiDecisionMapper.findPendingDecisions("CREATED", since);
// for each: BUY/SELL 무관하게 RiskService → OrderService.placeOrder 위임
// (단, RiskService 내부에서 BUY/SELL 분기)
```

`AiDecisionMapper.findPendingDecisions` 신규 추가 (SQL에서 `decision IN ('BUY','SELL')` 또는 `decision != 'HOLD'`).

### F. TargetStopMonitorScheduler (신설) — 핵심

```java
@Scheduled(cron = "0 */1 9-15 * * MON-FRI")  // 매 1분
public void run() {
    if (!logSupport.isMarketOpen()) return;

    List<PortfolioPosition> heldList = portfolioPositionMapper.findAllHeld(accountNo);
    for (PortfolioPosition pos : heldList) {
        // 가장 최근 미만료 BUY AI 판단 1건 조회
        AiDecision buyDecision = aiDecisionMapper.findLatestBuyForActivePosition(pos.getStockCode());
        if (buyDecision == null) continue;
        
        BigDecimal current = brokerClient.getCurrentPrice(pos.getStockCode()).getCurrentPrice();
        SellTrigger trigger = decideTrigger(current, buyDecision);
        if (trigger == null) continue;

        // 이미 같은 trigger로 오늘 주문 있으면 스킵 (중복 방지)
        if (orderRequestMapper.existsTodaySellByTrigger(buyDecision.getId(), trigger)) continue;

        try {
            orderService.placeSellOrderByTrigger(buyDecision.getId(), trigger);
        } catch (Exception e) {
            log.warn("[TargetStop] 매도 실패 {} ({}): {}", pos.getStockCode(), trigger, e.getMessage());
        }
    }
}

private SellTrigger decideTrigger(BigDecimal current, AiDecision d) {
    if (d.getStopLossPrice() != null && current.compareTo(d.getStopLossPrice()) <= 0)
        return SellTrigger.STOP_LOSS_HIT;
    if (d.getTargetPrice() != null && current.compareTo(d.getTargetPrice()) >= 0)
        return SellTrigger.TARGET_HIT;
    return null;
}
```

→ `OrderService`에 `placeSellOrderByTrigger(decisionId, trigger)` public 메서드 추가 (수동/스케줄러 양쪽에서 사용).

### G. ExecutionSyncService — 실현 손익 기록

```java
// SELL 체결 시 추가 처리:
if ("SELL".equals(execution.getOrderSide())) {
    // 평균매수가는 portfolio_position에서 (갱신 전 값 미리 캡쳐)
    BigDecimal avgBuy = positionBeforeSync.getAveragePrice();
    BigDecimal realized = execution.getExecutedPrice()
        .subtract(avgBuy)
        .multiply(BigDecimal.valueOf(execution.getExecutedQuantity()));
    
    portfolioProfitLossMapper.insertIgnore(PortfolioProfitLoss.builder()
        .accountNo(accountNo)
        .stockCode(execution.getStockCode())
        .baseDate(LocalDate.now())
        .evaluationType("REALIZED")
        .startAssetAmount(avgBuy.multiply(BigDecimal.valueOf(execution.getExecutedQuantity())))
        .endAssetAmount(execution.getExecutedAmount())
        .unrealizedProfitLoss(realized)
        .returnRate(...)
        .build());
}
```

→ `portfolio_profit_loss` UK가 `account_no+stock_code+base_date+evaluation_type`이므로 하루 여러 번 SELL 시 UK 충돌 가능. **UK에 broker_order_no 또는 unique_id 추가 필요할 수도** → 사용자 승인 필요. 임시 우회: `evaluation_type='REALIZED'` 대신 `'REALIZED_'+broker_order_no` 같은 회피책 검토.

---

## Files / Changes

### 신규
- `order/policy/SellOrderPolicyEngine.java`
- `order/trigger/SellTrigger.java` (enum)
- `risk/rule/SellRiskManager.java`
- `scheduler/TargetStopMonitorScheduler.java`
- `plan/PROCESS-AI-ORDER.md` ✅ 이미 생성

### 수정
- `order/service/OrderService.java` — BUY/SELL 분기 + `placeSellOrder(...)` + `placeSellOrderByTrigger(...)`
- `order/service/ExecutionSyncService.java` — SELL 체결 시 `portfolio_profit_loss REALIZED` 기록
- `risk/service/RiskService.java` — BUY/SELL 분기 (`SellRiskManager` 호출)
- `scheduler/OrderExecutionScheduler.java` — `findPendingDecisions` 호출로 변경
- `ai/mapper/AiDecisionMapper.java(.xml)` — `findPendingDecisions`, `findLatestBuyForActivePosition` 추가
- `order/mapper/OrderRequestMapper.java(.xml)` — `existsTodaySellByTrigger`, `existsPartialSellForDecision` 추가
- `portfolio/mapper/PortfolioPositionMapper.java(.xml)` — `findAllHeld(accountNo)` 추가

### DB (사용자 승인 필요)
- `risk_policy_config`에 row 추가:
  - `MIN_SELL_CONFIDENCE` = 0.6
  - `TARGET_HIT_PARTIAL_RATIO` = 0.5
- (검토) `portfolio_profit_loss` UK 조정 (REALIZED 다건 저장 위해) — **DDL 변경은 보고 후 결정**

---

## Test Steps

1. `SellOrderPolicyEngineTest` (Mockito 단위)
   - AI_DECISION + weight=0 → 전량
   - AI_DECISION + weight=0.05 → 목표비중 초과분만 매도
   - TARGET_HIT 1차 → 50%
   - TARGET_HIT 2차 (`hasPartial=true`) → 잔여 전량
   - STOP_LOSS_HIT → 전량
   - 1주 보유 시 분할 불가 → 전량
2. `SellRiskManagerTest` — 보유 없음/수량 초과/최소수량 케이스
3. `TargetStopMonitorSchedulerTest` (Mockito) — 트리거 판정 로직 + 중복 방지
4. `OrderServiceSellTest` (Mockito) — `placeSellOrder` 흐름
5. `ExecutionSyncServiceTest` 확장 — SELL 체결 시 `portfolio_profit_loss` 저장 검증

수동 검증 (test 프로파일):
- 보유 종목 1건 만든 후 `POST /api/orders/sell/manual?stockCode=...` (신규 엔드포인트) 호출
- 또는 AI에 SELL을 강제로 생성하는 테스트 API

---

## Risks / Assumptions

- **트랜잭션 내 외부 API**: `placeSellOrder`도 트랜잭션 내에서 KIS 호출 → 타임아웃 시 READY로 남음. `OrderExecutionScheduler` 다음 사이클에서 재처리되지 않음 (재시도 로직 별도 필요).
- **트리거 중복 방지**: `existsTodaySellByTrigger`는 같은 (decisionId, trigger, today)면 차단. 분할 익절 1차/2차가 같은 날이면 차수 필드 필요. → **trigger enum에 `TARGET_HIT_1`, `TARGET_HIT_2` 분리** 채택.
- **`portfolio_profit_loss` UK 충돌**: 하루 SELL 여러 번 시 REALIZED 행 충돌 가능 → DDL 검토 필요 (사용자 보고).
- **현재가 조회 실패 시 SELL 차단**: STOP_LOSS는 현재가 없이도 강제 매도해야 안전한데, 현재 설계는 차단함. → 추후 정책 추가 검토.
- **AI SELL 판단 발생 빈도**: 현재 `AiDecisionScheduler`가 모든 active stock 순회 → AI가 보유 안 한 종목에 SELL을 낼 수도 있음. `placeSellOrder`에서 "보유 없음" 예외로 차단되므로 문제 없으나 로그 노이즈 발생 가능.

---

## 단계별 진행 순서 (별도 plan 분할)

Plan 19 (이 문서) — 설계 확정
Plan 20 — SellOrderPolicyEngine + SellRiskManager 구현 + 단위 테스트
Plan 21 — OrderService 리팩토링 + OrderExecutionScheduler 변경 + AI SELL 흐름 통합
Plan 22 — TargetStopMonitorScheduler 신설 (수익 실현 핵심)
Plan 23 — ExecutionSyncService SELL 체결 후 실현 손익 기록 + DDL 검토
Plan 24 — 통합 테스트 + 수동 검증 시나리오

---

## 사용자 승인 필요

이 설계 그대로 Plan 20부터 진행해도 되는지 확인.
특히 다음 두 가지는 명시적 OK 부탁:
- (a) `SellOrderPolicyEngine`을 별도 클래스로 신설 (OrderPolicyEngine과 분리)
- (b) `SellTrigger` enum에 `TARGET_HIT_1`, `TARGET_HIT_2` 분리 vs 단일 `TARGET_HIT` + 차수 별도 필드
- (c) `portfolio_profit_loss` UK 변경이 필요해질 경우 그때 보고하고 진행

