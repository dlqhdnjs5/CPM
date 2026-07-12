## Understanding

The dashboard shows PAPER total assets and available cash, but it does not make total profit/loss obvious. The latest `paper_account_balance` already exposes `total_profit_loss_amount` and `total_profit_loss_rate`, so this can be added without a DB schema change.

## Implementation Plan

1. Add a `Total P/L` metric card to the dashboard overview.
2. Show signed profit/loss amount as the main value.
3. Show signed profit/loss percentage as the supporting value.
4. Color positive values green and negative values red.
5. Adjust the overview grid to fit the extra metric cleanly.

## Files / Changes

- `src/main/resources/static/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/dashboard.css`

## Test Steps

- Run `gradlew processResources`.
- Refresh `/dashboard.html` after app restart or resource reload.

## Risks / Assumptions

- No DB schema change is needed.
- `paperBalance.totalProfitLossAmount` and `paperBalance.totalProfitLossRate` are already present in the summary response.
