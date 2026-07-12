# Plan 20: 주문 안정성 + PAPER 기반

## Understanding

현재 `OrderService.placeOrder()`는 `@Transactional` 안에서 KIS 주문 API를 호출한다.
주문 실패/타임아웃 시 `order_request`, `order_status_history`, `broker_api_log`가 함께 롤백될 수 있어 자동매매 운영에 위험하다.

첫 운영 목표는 PAPER 우선이다. 기본 설정은 실제 주문이 나가지 않는 `PAPER`로 둔다.

## Implementation Plan

- `cpm.trading.mode` 기본값을 `PAPER`, `enabled` 기본값을 `false`로 변경한다.
- `TradingProperties`를 추가해 `PAPER / REAL` 모드를 명시적으로 판정한다.
- `OrderRequestCreateService`를 신설해 `order_request READY`와 READY 이력을 별도 트랜잭션으로 먼저 저장한다.
- `OrderStateService`를 신설해 ORDERED/FILLED/FAILED 상태 변경과 이력 저장을 별도 트랜잭션으로 보존한다.
- `PaperOrderExecutor`를 추가해 KIS 호출 없이 가상 주문번호, 가상 체결, PAPER 포지션 갱신을 수행한다.
- `KisOrderExecutor`를 추가해 REAL 모드의 실제 KIS 주문 호출을 분리한다.
- `OrderExecutor`는 trading mode에 따라 PAPER/REAL executor를 선택한다.

## Files / Changes

- `application.yml`
- `common/config/TradingProperties.java`
- `order/service/OrderRequestCreateService.java`
- `order/service/OrderStateService.java`
- `order/executor/PaperOrderExecutor.java`
- `order/executor/KisOrderExecutor.java`
- `order/executor/OrderExecutor.java`
- `order/service/OrderService.java`

## Test Steps

- PAPER 모드에서 KIS 주문 API가 호출되지 않는지 확인한다.
- 주문 요청 생성 후 executor 실패 시 DB 상태가 FAILED로 남는지 확인한다.
- `./gradlew.bat test` 전체 통과.

## Risks / Assumptions

- `cpm.trading.enabled=false`는 REAL 주문 차단 안전장치로 사용한다.
- PAPER 모드에서는 `enabled=false`여도 가상 주문은 허용한다.
