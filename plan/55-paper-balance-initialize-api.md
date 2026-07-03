# Plan 55: PAPER Balance Initialize API

## Understanding

PAPER trading requires an initial row in `paper_account_balance`. The service method already exists as `PaperPortfolioService.ensureAccountInitialized(accountNo, seedCash)`, but there is no API to call it directly before risk checks or paper orders.

## Implementation Plan

1. Add an admin-facing account API to initialize PAPER balance with an explicit `seedCash` request parameter.
2. Reuse the configured KIS account number as the PAPER account key, matching the existing PAPER ledger rule.
3. Return the latest PAPER balance after initialization.
4. Add controller test coverage.

## Files / Changes

- `AccountController`: add `POST /api/account/paper/balance/init?seedCash=...`.
- `AccountControllerTest`: verify the endpoint delegates to `PaperPortfolioService`.

## Test Steps

- Run focused controller test.
- Compile Java/test sources if needed.

## Risks / Assumptions

- The API is idempotent because `ensureAccountInitialized` does not overwrite an existing PAPER balance.
- The user must pass the seed cash explicitly; the code should not guess the starting capital.
