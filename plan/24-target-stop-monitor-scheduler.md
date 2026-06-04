# Plan 24: 자동 익절/손절 스케줄러

## Understanding

수익 실현과 손실 방어를 위해 보유 종목의 목표가/손절가 도달을 장중 감시해야 한다.

## Implementation Plan

- `TargetStopMonitorScheduler`를 신설한다.
- 보유 수량이 있는 포지션을 조회한다.
- 각 포지션의 최신 체결 BUY 판단을 조회한다.
- 현재가가 손절가 이하이면 `STOP_LOSS_HIT` 전량 매도.
- 현재가가 목표가 이상이면 `TARGET_HIT_1`, 기존 1차 익절이 있으면 `TARGET_HIT_2`.
- 같은 판단/트리거/일자 중복 SELL 주문을 방지한다.

## Files / Changes

- `scheduler/TargetStopMonitorScheduler.java`
- `portfolio/mapper/PortfolioPositionMapper.java/.xml`
- `ai/mapper/AiDecisionMapper.java/.xml`
- `order/mapper/OrderRequestMapper.java/.xml`
- `order/service/OrderService.java`

## Test Steps

- 목표가 1차/2차 익절 분기 테스트.
- 손절 전량 매도 테스트.
- 중복 주문 방지 테스트.

## Risks / Assumptions

- 현재가 조회 실패 시 해당 종목은 스킵한다.
