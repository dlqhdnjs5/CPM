## Understanding

AI 결정 프롬프트에 연준/FOMC/한국은행 등 시장 전체에 영향을 주는 매크로 뉴스를 함께 넣는다.
단, 매크로 뉴스는 개별 종목의 BUY/SELL을 직접 결정하는 신호가 아니라 금리, 유동성, 환율, 시장 위험을 보정하는 컨텍스트로 사용한다.

## Implementation Plan

1. 기존 `stock_news`, `news_ai_summary`, `news_sentiment` 테이블을 재사용한다.
2. 매크로 뉴스는 가상 stock_code `MACRO_FED`, `MACRO_BOK`, `MACRO_MARKET` 으로 저장한다.
3. 매크로 뉴스 수집 API를 추가한다.
4. 저장/분석된 매크로 뉴스를 최근 기간 기준으로 집계하여 `MacroContext`를 만든다.
5. AI 결정 프롬프트의 inputJson에 `macroContext`를 추가한다.
6. 시스템 프롬프트에는 매크로 컨텍스트를 시장 리스크/유동성 보정값으로만 쓰도록 명시한다.

## Files / Changes

- `src/main/java/com/bowon/cpm/macro/domain/MacroContext.java`
- `src/main/java/com/bowon/cpm/macro/service/MacroNewsService.java`
- `src/main/java/com/bowon/cpm/macro/service/MacroContextService.java`
- `src/main/java/com/bowon/cpm/admin/MacroController.java`
- `src/main/java/com/bowon/cpm/ai/service/AiDecisionService.java`
- `src/main/java/com/bowon/cpm/ai/prompt/AiDecisionPromptBuilder.java`
- `src/main/java/com/bowon/cpm/news/mapper/StockNewsMapper.java`
- `src/main/resources/mapper/news/StockNewsMapper.xml`

## Test Steps

1. `MacroContextServiceTest`로 FED/BOK 뉴스 집계와 hawkish/dovish 분류를 검증한다.
2. `AiDecisionPromptBuilderTest`로 `macroContext`가 프롬프트 JSON에 들어가는지 검증한다.
3. 관련 단위 테스트와 컴파일을 반복 실행한다.
4. 앱이 떠 있으면 curl로 매크로 뉴스 수집 API를 호출해 실제 요청 흐름을 확인한다.

## Risks / Assumptions

- DB 스키마 변경은 하지 않는다.
- `MACRO_*` 가상 코드는 실제 종목 코드가 아니므로 주문/리스크/포트폴리오 흐름에 사용하지 않는다.
- 기존 뉴스 AI 분석은 종목 영향 중심이므로, 첫 단계에서는 sentiment/impact와 키워드 기반 stance를 보조적으로 집계한다.
