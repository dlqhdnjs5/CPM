# Plan 23: SELL 리스크 + AI SELL 주문 흐름 연결

## Understanding

현재 주문 스케줄러는 `findPendingBuyDecisions()`만 호출하므로 AI SELL 판단은 주문으로 이어지지 않는다.

## Implementation Plan

- `SellRiskManager`를 신설한다.
- `RiskService`에서 BUY는 기존 룰, SELL은 보유 수량/신뢰도 중심 룰로 분기한다.
- `AiDecisionMapper.findPendingDecisions()`를 추가해 BUY/SELL CREATED 판단을 모두 조회한다.
- `OrderExecutionScheduler`가 BUY/SELL 판단을 모두 처리한다.
- `OrderService`에서 BUY/SELL 정책 엔진을 분기한다.

## Files / Changes

- `risk/rule/SellRiskManager.java`
- `risk/service/RiskService.java`
- `ai/mapper/AiDecisionMapper.java/.xml`
- `scheduler/OrderExecutionScheduler.java`
- `order/service/OrderService.java`

## Test Steps

- 보유 수량 없음, 매도 가능 수량 부족, 최소 수량 실패 단위 테스트.
- SELL 판단이 스케줄러 조회 대상에 포함되는지 확인.

## Risks / Assumptions

- SELL AI 판단의 confidence 검증은 기존 `DEFAULT_RISK_POLICY.min_confidence`를 사용한다.
