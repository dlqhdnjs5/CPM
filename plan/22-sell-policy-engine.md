# Plan 22: SELL 정책 엔진

## Understanding

BUY는 예수금 기준으로 수량을 계산하지만, SELL은 보유 수량과 매도 트리거 기준으로 계산해야 한다.

## Implementation Plan

- `BuyOrderPolicyEngine`을 추가하고 기존 BUY 계산식을 명확히 분리한다.
- `SellTrigger` enum을 추가한다.
- `SellOrderPolicyEngine`을 신설한다.
- SELL 계산 기준:
  - `AI_DECISION + weight=0`: 전량 매도
  - `AI_DECISION + weight>0`: 목표 잔여 비중까지 축소
  - `TARGET_HIT_1`: 보유 수량 50%, 1주 보유 시 전량
  - `TARGET_HIT_2`: 잔여 전량
  - `STOP_LOSS_HIT`: 전량

## Files / Changes

- `order/policy/BuyOrderPolicyEngine.java`
- `order/policy/SellOrderPolicyEngine.java`
- `order/trigger/SellTrigger.java`
- `order/service/OrderService.java`

## Test Steps

- SELL 트리거별 수량 계산 단위 테스트.
- 수량 0 케이스는 주문 불가로 반환되는지 확인.

## Risks / Assumptions

- SELL 계산 엔진은 DB/KIS 호출 없는 순수 계산 컴포넌트로 유지한다.
