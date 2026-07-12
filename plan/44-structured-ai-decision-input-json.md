## Understanding

The current AI decision prompt sends stock, account, chart, news, disclosure,
financial, event, and feedback data as long natural-language sections. That
works, but it leaves too much interpretation to the model and can over-weight
repeated news/disclosure text.

The requested direction is sound: CPM should transform collected source data
into a compact decision-input JSON before calling OpenAI. The JSON should expose
only the fields the model needs for BUY / SELL / HOLD, and explicitly mark
missing or low-quality data.

## Implementation Plan

1. Replace the long text user prompt with a structured JSON input.
   - Keep the required output schema guidance in the system prompt.
   - Send one `inputJson` object containing stock, account, position,
     technical, price quality, news summary, disclosure summary, supply/demand,
     fundamental, risk/reward, and strategy feedback sections.

2. Add position context to AI input collection.
   - In PAPER mode, read `paper_portfolio_position`.
   - In REAL mode, read `portfolio_position`.
   - If no row or quantity is zero, mark `isHolding=false`.
   - Do not invent position values from text or news.

3. Add fundamental context from existing indicators.
   - Read the latest `stock_fundamental_indicator`.
   - Map growth, profitability, debt risk, PER/PBR/PSR, ROE, and total score.
   - Keep missing values as `null`.

4. Summarize raw inputs before sending.
   - News: counts, weighted sentiment, average impact, top 5 impact-ranked news.
   - Disclosure: event-type booleans, latest important disclosure, simple impact.
   - Price quality: realtime/daily close gap, latest volume abnormal flag, quality.
   - Risk/reward: recent support/resistance from daily prices.

5. Keep unavailable data explicit.
   - Market context and supply/demand are not fully implemented yet, so send
     their fields as `null`.
   - The model rule should treat missing/LOW-quality data conservatively.

## Files / Changes

- Change:
  - `src/main/java/com/bowon/cpm/ai/prompt/AiDecisionPromptBuilder.java`
  - `src/main/java/com/bowon/cpm/ai/service/AiDecisionService.java`
  - `src/test/java/com/bowon/cpm/ai/prompt/AiDecisionPromptBuilderTest.java`

- No DB schema change is planned.

## Test Steps

1. Run focused prompt-builder tests.
2. Run focused AI service compile/tests if available.
3. Run full Gradle test suite when the focused tests pass.

## Risks / Assumptions

- `volumeRatio20` is mapped as latest volume divided by the recent average
  volume, not from `stock_indicator_daily.volume_change_rate`.
- `marketContext` and `supplyDemand` stay null until their collectors exist.
- If `priceDataQuality.quality=LOW`, the prompt instructs the model to return
  HOLD with lower confidence.
