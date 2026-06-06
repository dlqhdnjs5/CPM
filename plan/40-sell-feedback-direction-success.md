## Understanding

`ai_feedback` currently marks a SELL decision as failed unless the target price is reached. For daily feedback this is too strict: if a SELL decision is followed by a price decline, the direction was correct even if the target was not fully reached.

## Implementation Plan

1. Keep target/stop-loss flags as they are.
2. Calculate feedback return from the decision perspective:
   - BUY: `(evaluated - base) / base`
   - SELL: `(base - evaluated) / base`
3. For `evaluateDecision` daily-style feedback, mark success by direction unless stop loss is reached:
   - BUY succeeds when evaluated price is above base price.
   - SELL succeeds when evaluated price is below base price.
   - Target reached remains an additional metric, not the only success condition.
4. Apply the same decision-perspective return calculation to holding-end feedback, while keeping target/stop flags.
5. Add tests for SELL direction success when target price is not reached.

## Files / Changes

- `src/main/java/com/bowon/cpm/feedback/service/FeedbackService.java`
- `src/test/java/com/bowon/cpm/feedback/service/FeedbackServiceTest.java`

## Test Steps

- Run `FeedbackServiceTest`.

## Risks / Assumptions

- Existing rows in `ai_feedback` are not automatically rewritten by this code change.
- `target_reached` keeps its original meaning, so a SELL can be directionally successful while `target_reached=false`.
