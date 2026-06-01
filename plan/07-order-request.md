# Plan: 7단계 - OrderRequest 생성 및 실전 주문 실행

## Understanding

리스크 검증 통과 후 실제 KIS API로 주문을 실행한다.

흐름:
```
RiskCheck(passed=true)
→ OrderPolicyEngine (주문 수량 계산)
→ order_request 생성 (idempotency_key)
→ KIS 주문 API 호출
→ order_status_history 저장
→ order_request.broker_order_no 업데이트
```

## 주문 수량 계산
```
주문 기준 금액 = 총 평가자산 × AI 추천 비중
실제 주문 가능 금액 = min(주문 기준 금액, 예수금)
주문 수량 = floor(실제 주문 가능 금액 / 현재가)
```
- 주문 수량 < 1 이면 주문 안 함

## idempotency_key 형식
```
{accountNo}:{stockCode}:{aiDecisionId}:{orderSide}:{yyyyMMddHHmm}
```
- 동일 key 존재 시 주문 생성 안 함 (중복 방지)

## Files
```
order/
  domain/OrderRequest.java
  domain/OrderStatusHistory.java
  mapper/OrderRequestMapper.java
  mapper/OrderStatusHistoryMapper.java
  policy/OrderPolicyEngine.java
  executor/OrderExecutor.java
  service/OrderService.java

admin/OrderController.java

resources/mapper/order/
  OrderRequestMapper.xml
  OrderStatusHistoryMapper.xml
```

