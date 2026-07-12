## Understanding

삼성전자 AI 판단이 계속 HOLD로 나오는 주된 이유는 프롬프트가 `riskRewardRatio < 1.0`, 큰 현재가 상승, 낮은 거래량, 기관 순매도, 매크로 부담을 모두 보수적으로 해석하도록 유도하기 때문이다.

특히 현재 `riskRewardRatio`는 최근 일봉의 고점/저점으로 계산된 참고값이다. 강한 돌파/모멘텀 국면에서는 최근 저항선이 현재가에 가까워 손익비가 낮게 계산될 수 있고, 이 값을 절대 차단 기준처럼 쓰면 신규 BUY가 거의 나오지 않는다.

## Implementation Plan

1. `riskReward` 섹션에 계산 기준을 명확히 추가한다.
   - 최근 support/resistance 기반 참고값임을 명시
   - breakout/momentum 국면에서는 AI가 더 합리적인 target/stop을 제안할 수 있게 한다.
2. 시스템 프롬프트의 BUY 회피 문구를 완화한다.
   - riskRewardRatio < 1.0은 BUY 금지가 아니라 포지션 크기 축소/신뢰도 조정 신호
   - 큰 현재가 상승은 과열일 수도 있지만 돌파 모멘텀일 수도 있음을 명시
   - 기관 순매도는 외국인/개인/뉴스/기술 신호와 함께 판단하게 한다.
3. BUY 허용 조건을 더 현실적으로 만든다.
   - HIGH 또는 MEDIUM 품질
   - currentPrice가 단기/중기 이평선 위
   - 뉴스/펀더멘털/시장 컨텍스트가 우호적
   - 치명적 공시/LOW 품질/보유 없는 SELL 없음
4. 테스트로 새 프롬프트 문구와 riskReward metadata가 포함되는지 확인한다.

## Files / Changes

- `AiDecisionPromptBuilder.java`
- `AiDecisionPromptBuilderTest.java`

## Test Steps

- `AiDecisionPromptBuilderTest`
- 필요 시 `AiDecisionServiceTest`

## Risks / Assumptions

- 주문 리스크 검증은 그대로 남아 있으므로 AI가 BUY를 내도 RiskManager가 최종 차단할 수 있다.
- 프롬프트 완화는 공격적 매수 강제가 아니라, 긍정 근거가 충분할 때 소액 BUY를 허용하는 방향이다.
