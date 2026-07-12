## Understanding

`AiDecisionService` currently collects account cash for the AI prompt by always calling `brokerClient.getAccountBalance()`. In PAPER mode this incorrectly sends real KIS cash/asset values to the AI prompt, while orders and risk checks use PAPER account state.

## Implementation Plan

1. Inject `TradingProperties`, `PaperPortfolioService`, and `KisProperties` into `AiDecisionService`.
2. In prompt input collection:
   - PAPER mode: read latest `paper_account_balance`.
   - REAL mode: keep using `brokerClient.getAccountBalance()`.
3. Do not seed PAPER balance from KIS inside AI prompt generation.
4. Add a unit test proving PAPER prompt input uses paper balance and does not call KIS account balance.

## Files / Changes

- `src/main/java/com/bowon/cpm/ai/service/AiDecisionService.java`
- `src/test/java/com/bowon/cpm/ai/service/AiDecisionServiceTest.java`

## Test Steps

- Run `AiDecisionServiceTest`.
- Run compile if constructor wiring needs verification.

## Risks / Assumptions

- If PAPER balance is not initialized, the AI prompt will omit account cash/asset values instead of falling back to KIS.
