## Understanding

The AI prompt currently treats a large gap between realtime current price and the latest confirmed daily close as `priceDataQuality=LOW`. That is wrong during market hours because the latest confirmed daily close is usually yesterday's close and the realtime price naturally differs. The gap is market movement, not data corruption.

## Implementation Plan

1. Stop using `currentPriceDailyCloseGapRate` as a LOW data-quality condition.
2. Move realtime-vs-latest-close movement into a new `marketMove` section.
3. Move volume ratio abnormality into market/liquidity interpretation instead of data quality.
4. Keep `priceDataQuality=LOW` only for actual missing/corrupt daily price data.
5. Update tests so large market moves no longer force LOW, while incomplete latest daily candles are still excluded.

## Files / Changes

- `AiDecisionPromptBuilder.java`
- `AiDecisionPromptBuilderTest.java`

## Test Steps

- Run `AiDecisionPromptBuilderTest`.

## Risks / Assumptions

- AI can still decide HOLD when a large move creates bad risk-reward or weak context, but it should not call that "low data quality".
