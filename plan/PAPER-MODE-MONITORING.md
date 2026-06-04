# PAPER 모드 실행 확인 런북

## 목적

PAPER 모드로 CPM을 실행했을 때 자동 매수/매도 흐름이 정상적으로 이어지는지 DB 기준으로 확인한다.

확인 흐름은 다음 순서로 본다.

```text
AI 판단
→ 리스크 검증
→ PAPER 주문 요청
→ 가상 체결
→ 포지션 반영
→ SELL 실현손익 기록
→ 피드백/성과 집계
```

PAPER 모드에서는 실제 KIS 주문 API가 호출되면 안 된다. 주문 실행 로그는 `broker_type='PAPER'`, `request_url='paper://order'`로 남아야 한다.

---

## 1. 스케줄러 실행 상태

```sql
SELECT *
FROM scheduler_execution_log
ORDER BY id DESC
LIMIT 50;
```

중점 확인 스케줄러:

- `AiDecisionScheduler`
- `RiskCheckScheduler`
- `OrderExecutionScheduler`
- `TargetStopMonitorScheduler`
- `ExecutionSyncScheduler`
- `DailyProfitLossScheduler`

정상 기준:

- 최근 실행의 `status`가 `SUCCESS`
- 오래 고착된 `RUNNING`이 없어야 함
- 실패 시 `execution_message`에 원인이 남아야 함

---

## 2. AI 판단

```sql
SELECT id, stock_code, decision, confidence, current_price,
       target_price, stop_loss_price, decision_status, created_at
FROM ai_decision
ORDER BY id DESC
LIMIT 50;
```

AI raw response도 함께 본다.

```sql
SELECT id, stock_code, is_parsed, parse_error, created_at
FROM ai_decision_raw_response
ORDER BY id DESC
LIMIT 50;
```

정상 기준:

- `ai_decision_raw_response.is_parsed = 1`
- `parse_error IS NULL`
- `ai_decision.decision IN ('BUY', 'SELL', 'HOLD')`
- 주문 대상 판단은 `decision_status='CREATED'` 이후 리스크/주문 단계로 이동

---

## 3. 리스크 검증

```sql
SELECT *
FROM risk_check_result
ORDER BY id DESC
LIMIT 50;
```

정상 기준:

- 주문 가능한 판단은 `passed = 1`
- 차단된 판단은 `passed = 0`이고 `fail_reason`에 이유가 있어야 함

---

## 4. PAPER 주문 요청

가장 중요한 확인 테이블이다.

```sql
SELECT id, ai_decision_id, broker_type, stock_code, order_side,
       order_quantity, order_amount, order_status,
       broker_order_no, request_reason, requested_at
FROM order_request
ORDER BY id DESC
LIMIT 50;
```

PAPER 정상 기준:

- `broker_type = 'PAPER'`
- `order_status = 'FILLED'`
- `broker_order_no`가 `PAPER-...` 형식
- BUY 주문은 `order_side = 'BUY'`
- SELL 주문은 `order_side = 'SELL'`

SELL `request_reason` 예시:

```text
SELL[AI_DECISION]: ...
SELL[TARGET_HIT_1]: ...
SELL[TARGET_HIT_2]: ...
SELL[STOP_LOSS_HIT]: ...
```

---

## 5. 주문 상태 이력

```sql
SELECT *
FROM order_status_history
ORDER BY id DESC
LIMIT 100;
```

PAPER 정상 흐름:

```text
READY → ORDERED → FILLED
```

실패 시에도 `FAILED` 상태와 실패 사유가 남아야 한다.

---

## 6. 가상 체결

```sql
SELECT *
FROM order_execution
ORDER BY id DESC
LIMIT 50;
```

정상 기준:

- `broker_order_no`가 `PAPER-...` 형식
- `order_side`가 `BUY` 또는 `SELL`
- `executed_quantity` 존재
- `executed_price` 존재
- `executed_amount` 존재

---

## 7. 보유 포지션

```sql
SELECT *
FROM portfolio_position
ORDER BY updated_at DESC
LIMIT 50;
```

PAPER BUY 후 정상 기준:

- `quantity` 증가
- `average_buy_price` 갱신
- `available_quantity` 갱신

PAPER SELL 후 정상 기준:

- `quantity` 감소
- 전량 매도 시 `quantity = 0`
- `valuation_amount`, `profit_loss_amount`, `profit_loss_rate` 갱신

---

## 8. SELL 실현손익

SELL 체결이 실제로 수익/손실로 기록됐는지 확인하는 핵심 테이블이다.

```sql
SELECT *
FROM portfolio_realized_profit_loss
ORDER BY realized_at DESC
LIMIT 50;
```

정상 기준:

- SELL 체결마다 1건 생성
- `order_execution_id` 기준 중복 없음
- `buy_amount` 존재
- `sell_amount` 존재
- `realized_profit_loss` 존재
- `return_rate` 존재

---

## 9. PAPER 주문이 KIS 주문 API를 호출하지 않았는지 확인

```sql
SELECT broker_type, api_name, request_url, success, error_message, called_at
FROM broker_api_log
ORDER BY id DESC
LIMIT 100;
```

PAPER 주문 정상 기준:

- 주문 실행 로그의 `broker_type = 'PAPER'`
- 주문 실행 로그의 `request_url = 'paper://order'`

주의:

- 현재가 조회, 잔고 조회 등은 여전히 KIS 로그가 남을 수 있다.
- 중요한 기준은 주문 실행 API가 KIS로 나가지 않는 것이다.

---

## 핵심 테이블 요약

PAPER 모드 정상 동작은 아래 5개 테이블을 보면 가장 빠르게 판단할 수 있다.

```text
order_request
order_status_history
order_execution
portfolio_position
portfolio_realized_profit_loss
```

운영 전체 흐름까지 보려면 아래 테이블을 추가로 본다.

```text
scheduler_execution_log
ai_decision_raw_response
ai_decision
risk_check_result
broker_api_log
portfolio_profit_loss
```

---

## 현재 구조상 주의점

현재 PAPER 체결은 `portfolio_position`을 갱신하고 SELL 체결별 실현손익을 `portfolio_realized_profit_loss`에 기록한다.

다만 PAPER 전용 현금 장부는 아직 별도로 분리되어 있지 않다. 즉, PAPER BUY/SELL에 따른 예수금 증감까지 완전한 가상 계좌 장부로 관리하려면 추후 다음 중 하나가 필요하다.

- `paper_account_balance` 신규 테이블
- PAPER 전용 cash ledger 테이블
- 기존 `account_balance`와 분리된 PAPER 스냅샷 정책

따라서 현재 단계에서 PAPER 성과 확인은 포지션, 체결, 실현손익 중심으로 본다.
