# Plan: 8단계 - 체결 조회 + 포트폴리오 갱신

## Understanding

주문 후 체결 여부를 KIS API로 확인하고, 체결 결과를 order_execution에 저장한다.
체결 확인 후 portfolio_position을 KIS 실시간 잔고로 갱신한다.

## 흐름
```
GET /api/orders/executions/sync
→ KIS 당일 체결 내역 조회
→ order_request 상태 FILLED 업데이트
→ order_execution 저장
→ portfolio 갱신 (syncAccountBalance)
```

## KIS API
- 체결 조회: GET /uapi/domestic-stock/v1/trading/inquire-daily-ccld
- TR ID: TTTC8001R (실전 당일 체결)
- 주요 필드: ODNO(주문번호), CCLD_QTY(체결수량), CCLD_UNPR(체결단가), CCLD_AMT(체결금액)

## Files
```
order/
  domain/OrderExecution.java
  mapper/OrderExecutionMapper.java
  service/ExecutionSyncService.java

broker/kis/KisExecutionClient.java
broker/kis/dto/KisExecutionResponse.java

admin/ExecutionController.java

resources/mapper/order/OrderExecutionMapper.xml
```

