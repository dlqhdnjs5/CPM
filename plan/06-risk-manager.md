# Plan: 6단계 - RiskManager 리스크 검증

## Understanding

AI가 BUY 판단을 내려도 바로 주문하면 안 된다.
RiskManager가 다음 조건을 검증한 후 통과 시에만 주문 단계로 넘어간다.

## 검증 항목

1. AI 신뢰도(confidence) ≥ min_confidence
2. targetPrice > currentPrice (BUY 시)
3. stopLossPrice < currentPrice (BUY 시)
4. riskRewardRatio ≥ min_risk_reward_ratio
5. expectedLossRate ≥ max_expected_loss_rate (손실률 초과 차단)
6. 예수금(available_cash) ≥ 최소 주문 가능 금액
7. 종목별 현재 포지션 비중 ≤ max_position_weight
8. ai_decision.decision == HOLD → 자동 패스 (주문 불필요)

## Implementation Plan

- `RiskManager` — 검증 로직 (rule 패키지)
- `RiskService` — 검증 실행 + risk_check_result 저장
- `RiskCheckResult` domain
- `RiskCheckResultMapper` + XML
- `RiskPolicyConfig` domain
- `RiskPolicyConfigMapper` + XML (DB에서 정책 조회)
- Admin API: POST /api/risk/checks/{aiDecisionId}

## Files

```
risk/
  rule/RiskManager.java
  domain/RiskCheckResult.java
  domain/RiskPolicyConfig.java
  mapper/RiskCheckResultMapper.java
  mapper/RiskPolicyConfigMapper.java
  service/RiskService.java

admin/RiskController.java

resources/mapper/risk/
  RiskCheckResultMapper.xml
  RiskPolicyConfigMapper.xml
```

