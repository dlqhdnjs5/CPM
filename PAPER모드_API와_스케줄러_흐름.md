# PAPER 모드 확인 API와 스케줄러 흐름

## 1. PAPER 모드 확인 API

PAPER 모드에서 현재 가상 계좌가 어떻게 운용되고 있는지 확인할 때는 아래 API들을 호출한다.

### PAPER 계좌 잔고

```powershell
curl.exe "http://localhost:8080/api/account/paper/balance"
```

확인 대상 테이블:

```text
paper_account_balance
```

### PAPER 보유 종목

```powershell
curl.exe "http://localhost:8080/api/account/paper/positions"
```

확인 대상 테이블:

```text
paper_portfolio_position
```

### PAPER 주문 요청 이력

```powershell
curl.exe "http://localhost:8080/api/orders/requests?mode=PAPER&limit=50"
```

확인 대상 테이블:

```text
order_request
```

주요 확인 컬럼:

```text
broker_type = PAPER
order_side
order_status
stock_code
order_quantity
order_amount
idempotency_key
requested_at
```

### PAPER 체결 이력 - 종목별

```powershell
curl.exe "http://localhost:8080/api/orders/executions?stockCode=005930&mode=PAPER&limit=50"
```

확인 대상 테이블:

```text
order_execution
order_request
```

종목을 바꿔서 확인하려면 `stockCode`만 변경한다.

예:

```powershell
curl.exe "http://localhost:8080/api/orders/executions?stockCode=042700&mode=PAPER&limit=50"
```

### PAPER 성과 요약

```powershell
curl.exe "http://localhost:8080/api/admin/performance/paper?limit=20"
```

확인 대상 테이블:

```text
paper_portfolio_profit_loss
paper_account_balance
paper_portfolio_position
```

## 2. 실제 계좌와 PAPER 계좌 테이블 구분

실제 KIS 계좌:

```text
account_balance
portfolio_position
portfolio_profit_loss
```

PAPER 가상 계좌:

```text
paper_account_balance
paper_portfolio_position
paper_portfolio_profit_loss
```

실제 계좌 보유 종목을 DB에 반영하려면 먼저 KIS 잔고 동기화를 실행해야 한다.

```powershell
curl.exe "http://localhost:8080/api/account/balance"
```

그 다음 실제 보유 종목을 조회한다.

```powershell
curl.exe "http://localhost:8080/api/account/positions"
```

## 3. AI 판단 생성 스케줄러

AI 판단은 `AiDecisionScheduler`가 생성한다.

파일:

```text
src/main/java/com/bowon/cpm/scheduler/AiDecisionScheduler.java
```

실행 시간:

```text
09:30
10:00 ~ 14:00 매 정각
10:30 ~ 14:30 매 30분
15:00
```

처리 흐름:

```text
stock_master.findAllActive()
→ 각 active 종목마다 aiDecisionService.generateDecision(stockCode)
→ ai_prompt_log 저장
→ ai_decision_raw_response 저장
→ ai_decision 저장
→ ai_decision_factor 저장
```

생성된 AI 판단의 초기 상태:

```text
ai_decision.decision_status = CREATED
```

## 4. AI 판단 이후 주문 처리 스케줄러

AI 판단 이후 실제 리스크 검증과 주문 처리는 `OrderExecutionScheduler`가 담당한다.

파일:

```text
src/main/java/com/bowon/cpm/scheduler/OrderExecutionScheduler.java
```

실행 시간:

```text
09:35
10:05 ~ 15:05 매 정각 + 5분
10:35 ~ 14:35 매 30분 + 5분
```

즉 AI 판단 생성 약 5분 뒤에 주문 처리 스케줄러가 돈다.

## 5. 주문 처리 대상 조건

`OrderExecutionScheduler`는 `ai_decision` 전체를 처리하지 않는다.

처리 대상:

```text
decision_status = CREATED
decision IN ('BUY', 'SELL')
created_at >= 현재시간 - 35분
종목별 최신 1건
```

`HOLD` 판단은 주문 대상이 아니다.

오래된 판단은 주문하지 않고 만료 처리한다.

```text
decision_status = EXPIRED
```

## 6. 리스크 검증 흐름

`OrderExecutionScheduler`는 주문하기 전에 `RiskService`를 호출한다.

파일:

```text
src/main/java/com/bowon/cpm/risk/service/RiskService.java
```

처리 흐름:

```text
ai_decision 조회
→ risk_policy_config 조회
→ 현재 거래 모드 확인
→ PAPER 모드면 paper_account_balance / paper_portfolio_position 기준
→ REAL 모드면 KIS 계좌 동기화 후 account_balance / portfolio_position 기준
→ BUY면 RiskManager 검증
→ SELL이면 SellRiskManager 검증
→ risk_check_result 저장
```

리스크 결과 저장 테이블:

```text
risk_check_result
```

보유하지 않은 종목에 `SELL` 판단이 생성된 경우, 정상 흐름에서는 `SellRiskManager`가 보유 수량 없음으로 주문을 차단해야 한다.

## 7. 주문 생성과 체결 흐름

리스크 검증을 통과하면 `OrderService`가 주문을 생성한다.

파일:

```text
src/main/java/com/bowon/cpm/order/service/OrderService.java
```

처리 흐름:

```text
ai_decision 조회
→ 최신 risk_check_result 통과 여부 확인
→ idempotency_key 중복 확인
→ 주문 수량 계산
→ order_request 생성
→ OrderExecutor 실행
```

주문 요청 생성 시 상태:

```text
order_request.order_status = READY
```

## 8. PAPER 모드 주문 실행

PAPER 모드에서는 `PaperOrderExecutor`가 즉시 가상 체결 처리한다.

파일:

```text
src/main/java/com/bowon/cpm/order/executor/PaperOrderExecutor.java
```

처리 흐름:

```text
order_request READY
→ PAPER broker_order_no 생성
→ order_request ORDERED
→ order_execution 저장
→ paper_portfolio_position 갱신
→ paper_account_balance 갱신
→ order_request FILLED
→ order_status_history 저장
```

## 9. REAL 모드 주문 실행

REAL 모드에서는 KIS 주문 API로 실제 주문을 전송한다.

처리 흐름:

```text
order_request READY
→ KIS 주문 API 호출
→ order_request ORDERED
→ ExecutionSyncScheduler가 체결 조회
→ order_execution 저장
→ portfolio_position 갱신
→ account_balance 갱신
→ order_request FILLED
```

REAL 모드는 `cpm.trading.enabled=false`이면 주문이 차단된다.

## 10. 상태를 볼 때 기준 테이블

AI 판단 자체:

```text
ai_decision
ai_decision_raw_response
ai_decision_factor
```

리스크 검증:

```text
risk_check_result
```

주문 요청:

```text
order_request
order_status_history
```

체결:

```text
order_execution
```

PAPER 보유 상태:

```text
paper_account_balance
paper_portfolio_position
paper_portfolio_profit_loss
```

REAL 보유 상태:

```text
account_balance
portfolio_position
portfolio_profit_loss
```

## 11. 주의할 점

현재 코드상 `ai_decision.decision_status`는 주문 처리 전체 상태를 완전히 따라가지 않는다.

실제로 업데이트되는 주요 상태:

```text
CREATED
HOLD_BY_REVIEW
EXPIRED
```

리스크 통과/실패, 주문 접수, 체결 여부는 아래 테이블을 기준으로 확인해야 한다.

```text
risk_check_result
order_request
order_status_history
order_execution
paper_portfolio_position
portfolio_position
```
