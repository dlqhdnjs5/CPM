# Plan: 9단계 - AI 피드백 (ai_feedback + portfolio_profit_loss)

## Understanding

AI가 판단한 결과(BUY/SELL/HOLD)가 실제로 맞았는지 평가한다.

핵심 흐름:
```
ai_decision (판단 당시 currentPrice, targetPrice, stopLossPrice)
→ 현재가 조회 (KIS)
→ 수익률 계산
→ 목표가/손절가 도달 여부 판단
→ 판단 성공 여부 판단
→ ai_feedback 저장
→ portfolio_profit_loss 저장
→ 다음 AI 판단 프롬프트에 피드백 요약 포함
```

---

## Implementation Plan

### 1. FeedbackService
- `evaluateDecision(Long aiDecisionId, String evaluationType)` — 단건 평가
  - ai_decision 조회 (currentPrice, targetPrice, stopLossPrice)
  - 현재가 조회 (KIS)
  - return_rate = (현재가 - 판단당시가) / 판단당시가 * 100
  - target_reached = 현재가 >= targetPrice (BUY 기준)
  - stop_loss_reached = 현재가 <= stopLossPrice (BUY 기준)
  - success = target_reached == true AND stop_loss_reached == false
  - ai_feedback 저장 (UK: ai_decision_id + evaluation_type — 중복 방지)

### 2. PortfolioProfitLossService (또는 FeedbackService 내 통합)
- 일간 수익률 계산 → portfolio_profit_loss 저장
  - account_balance에서 오늘 자산 조회
  - account_balance에서 어제 자산 조회
  - return_rate 계산 후 저장 (UK: account_no + stock_code + base_date + evaluation_type)

### 3. AiDecisionPromptBuilder 피드백 포함
- buildUserPrompt()에 `feedbackSummary` 파라미터 추가
- 최근 3건 피드백 요약을 프롬프트에 포함

### 4. Mapper + XML
- `AiFeedbackMapper` + `AiFeedbackMapper.xml`
- `PortfolioProfitLossMapper` + `PortfolioProfitLossMapper.xml`

### 5. Domain 클래스
- `AiFeedback`
- `PortfolioProfitLoss`

### 6. Admin API
- `POST /api/feedback/decisions/{aiDecisionId}` — 단건 AI 판단 피드백 생성
- `GET  /api/feedback/decisions/{aiDecisionId}` — 피드백 조회
- `POST /api/feedback/portfolio/daily` — 일간 포트폴리오 수익률 저장

---

## Files / Changes

```
feedback/
  domain/AiFeedback.java
  domain/PortfolioProfitLoss.java
  mapper/AiFeedbackMapper.java
  mapper/PortfolioProfitLossMapper.java
  service/FeedbackService.java

admin/
  FeedbackController.java

resources/mapper/feedback/
  AiFeedbackMapper.xml
  PortfolioProfitLossMapper.xml

ai/prompt/AiDecisionPromptBuilder.java  (피드백 요약 파라미터 추가)
ai/service/AiDecisionService.java       (피드백 조회 후 프롬프트에 포함)
```

---

## Test Steps

```
1. POST /api/ai/decisions/005930        → AI 판단 생성 (id: N)
2. POST /api/feedback/decisions/N       → 피드백 평가
3. GET  /api/feedback/decisions/N       → 결과 확인 (return_rate, success 등)
4. POST /api/feedback/portfolio/daily   → 일간 수익률 저장
5. POST /api/ai/decisions/005930        → 재판단 (프롬프트에 피드백 포함됐는지 확인)
```

---

## Risks / Assumptions

- `ai_feedback` UK: `ai_decision_id + evaluation_type` → 동일 판단에 대한 DAILY/WEEKLY/MONTHLY 각각 1건만 저장
- 현재가 조회는 KIS API 호출 — 장 외 시간에는 직전 종가로 처리됨
- BUY 기준으로만 success 판단 (SELL은 반대 로직 적용)
- `portfolio_profit_loss.stock_code`는 NULL 허용 — 전체 포트폴리오 집계 시 NULL로 저장
- 피드백 요약은 ai_feedback.feedback_summary 컬럼 사용

