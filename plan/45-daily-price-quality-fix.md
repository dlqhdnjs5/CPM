## Understanding

AI decision input started marking Samsung Electronics as LOW quality because the latest daily candle for 2026-06-09 had an incomplete intraday-like value: OHLC all equal and volume 577. The AI prompt has a hard rule that LOW data quality must not produce BUY or SELL, so HOLD was expected once that bad candle became the latest daily price.

## Implementation Plan

1. Stop stale incomplete daily candles from being permanently fixed by `INSERT IGNORE`.
2. Exclude clearly incomplete/latest abnormal daily candles from AI prompt quality and technical/risk-reward input when a safer prior candle is available.
3. Keep schema unchanged.
4. Add focused tests around the prompt builder behavior.

## Files / Changes

- `StockPriceDailyMapper.xml`
  - Change batch insert from ignore-only to upsert so corrected KIS daily data can replace stale early data.
- `AiDecisionPromptBuilder.java`
  - Build price/volume/quality/risk sections from validated daily prices.
  - Drop a latest candle when it is obviously incomplete compared with prior volume and current quote.
- `AiDecisionPromptBuilderTest.java`
  - Verify an incomplete latest candle does not force LOW quality when prior complete data exists.

## Test Steps

- Run the prompt builder test.
- Run market/AI related focused tests if needed.

## Risks / Assumptions

- This does not delete existing bad DB rows. It prevents prompt decisions from trusting them and allows future KIS fetches to update duplicates.
- Existing bad rows can be cleaned manually after code is fixed if desired.
