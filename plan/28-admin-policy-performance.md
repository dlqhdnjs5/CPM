# Plan 28: Admin 정책/성과 API

## Understanding

자동매매 품질을 운영 중 조정하려면 리스크/전략 정책을 코드가 아니라 Admin API로 조회/수정할 수 있어야 한다.
또 PAPER 운전 결과를 빠르게 확인하려면 성과 조회 API도 필요하다.

## Implementation Plan

- `risk_policy_config` 조회/수정 API를 추가한다.
- `strategy_config` 도메인/매퍼/조회/수정 API를 추가한다.
- PAPER 성과 확인용 API를 추가한다.
  - 최근 `portfolio_profit_loss`
  - 최근 `portfolio_realized_profit_loss`
  - SELL 실현손익 합계/평균 수익률
- 기존 스키마를 사용한다.

## Files / Changes

- `admin/AdminPolicyController.java`
- `risk/mapper/RiskPolicyConfigMapper.java/.xml`
- `strategy/domain/StrategyConfig.java`
- `strategy/mapper/StrategyConfigMapper.java/.xml`
- `feedback/service/PerformanceQueryService.java`
- `feedback/mapper/PortfolioRealizedProfitLossMapper.java/.xml`
- 테스트: 컨트롤러/서비스 단위 테스트

## Test Steps

- 리스크 정책 조회/부분 수정 테스트
- 전략 설정 조회/부분 수정 테스트
- PAPER 성과 조회 테스트
- 전체 테스트 `./gradlew.bat test`

## Risks / Assumptions

- Trading mode 런타임 변경은 별도 상태 저장소가 필요하므로 이번 단계에서는 정책/성과 API에 집중한다.
- `config_json`은 문자열 JSON으로 관리하고, JSON 유효성 검증은 최소화한다.
