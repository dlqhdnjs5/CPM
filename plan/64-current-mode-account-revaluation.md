## Understanding

Dashboard refresh should become a two-step action:

1. Revalue/sync the current trading mode account.
2. Read `/api/dashboard/summary` from DB.

For PAPER mode, revaluation must fetch current quotes for held PAPER positions, update PAPER positions, insert a new PAPER account balance snapshot, and update daily PAPER P/L.

For REAL mode, the equivalent operation should use the existing KIS account/position sync path so switching `cpm.trading.mode=REAL` keeps the dashboard behavior the same from the user's perspective.

## Implementation Plan

1. Add PAPER revaluation to `PaperPortfolioService`.
   - Read held PAPER positions.
   - Fetch current price through `BrokerClient`.
   - Recalculate valuation, unrealized P/L amount, and P/L rate.
   - Upsert `paper_portfolio_position`.
   - Insert latest `paper_account_balance` snapshot using unchanged cash and recalculated evaluation.
   - Upsert `paper_portfolio_profit_loss`.

2. Add current-mode account revaluation service.
   - PAPER: call `PaperPortfolioService.revalue(accountNo)`.
   - REAL: call `PortfolioService.syncAccountBalance()`.

3. Add APIs.
   - `POST /api/account/revalue`: current-mode revalue.
   - `POST /api/account/paper/revalue`: explicit PAPER revalue alias.

4. Add scheduler.
   - `AccountRevaluationScheduler`
   - Runs during market hours every 5 minutes on weekdays.
   - Uses current mode:
     - PAPER: PAPER revalue.
     - REAL: KIS sync.
   - Logs to `scheduler_execution_log`.

5. Update dashboard refresh behavior.
   - Manual Refresh button calls `POST /api/account/revalue`.
   - Then reloads summary and AI trade history.
   - Automatic dashboard polling remains read-only; the scheduler handles background revaluation.

## Files / Changes

- `src/main/java/com/bowon/cpm/paper/service/PaperPortfolioService.java`
- `src/main/java/com/bowon/cpm/portfolio/service/AccountRevaluationService.java`
- `src/main/java/com/bowon/cpm/admin/AccountController.java`
- `src/main/java/com/bowon/cpm/scheduler/AccountRevaluationScheduler.java`
- `src/main/resources/static/dashboard.js`

## Test Steps

- Run `gradlew compileJava processResources`.
- Manually call `POST /api/account/revalue`.
- Confirm the latest balance table updates:
  - PAPER: `paper_account_balance`
  - REAL: `account_balance`

## Risks / Assumptions

- No DB schema change is needed.
- KIS quote/account APIs may fail; failures should fail the revalue action rather than corrupt existing DB state.
- In PAPER mode, if one quote fails, the service keeps that position's last stored price and still recalculates the account snapshot from available data.
