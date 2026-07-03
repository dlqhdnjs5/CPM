## Understanding

AI 결정 프롬프트의 `marketContext`는 현재 `emptyMarketContext()`로 고정되어 있어 KOSPI/KOSDAQ/섹터/환율/해외지수 값이 모두 null이다. DB의 `market_index` 테이블은 존재하지만 현재 데이터가 비어 있으므로, 단순 연결만으로는 문제가 해결되지 않는다.

또한 최근 guardrail이 보수적으로 누적되어 가격 품질이 LOW가 아닌 상황에서도 HOLD를 유도하는 문구가 강하다. 데이터가 충분하고 손익비/기술/뉴스/수급 중 일부가 정렬될 때는 제한된 BUY를 허용하도록 완화가 필요하다.

## Implementation Plan

1. `market_index` 최신 change_rate를 조회하는 mapper를 추가한다.
2. `market_index`가 비어 있을 경우 `stock_price_daily`와 `stock_master.market_type/sector_name`으로 시장/섹터 평균 등락률을 계산하는 fallback을 추가한다.
3. `AiDecisionService`에서 종목의 `market_type`, `sector_name`을 확인하고 marketContext를 수집한다.
4. `AiDecisionPromptBuilder`가 더 이상 empty marketContext를 쓰지 않고 실제 marketContext를 JSON에 넣도록 변경한다.
5. 시스템 프롬프트 guardrail을 완화한다.
   - LOW 품질은 계속 BUY/SELL 차단
   - MEDIUM 품질은 금지하지 않고 confidence/weight를 낮춘 신중한 BUY 허용
   - 수급/뉴스 일부 누락만으로 자동 HOLD하지 않도록 문구 조정
6. 단위테스트로 marketContext JSON 포함과 완화된 guardrail 문구를 확인한다.

## Files / Changes

- `market/domain/MarketContext.java`
- `market/mapper/MarketIndexMapper.java`
- `mapper/market/MarketIndexMapper.xml`
- `market/service/MarketContextService.java`
- `AiDecisionService.java`
- `AiDecisionPromptBuilder.java`
- `AiDecisionPromptBuilderTest.java`
- `AiDecisionServiceTest.java`

## Test Steps

- `AiDecisionPromptBuilderTest`
- `AiDecisionServiceTest`
- 필요 시 `compileJava processResources`

## Risks / Assumptions

- DB 스키마 변경은 없다.
- `soxIndexChangeRate`, `usdKrwChangeRate`는 `market_index`에 해당 코드 데이터가 없으면 null 유지한다.
- KOSPI/KOSDAQ/섹터는 fallback으로 내부 종목 일봉 평균을 사용한다.
