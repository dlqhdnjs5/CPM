## Understanding

The goal is not to guarantee profit, because that is impossible in the market. The practical engineering goal is to make the system much harder to ruin:

- Keep per-trade loss small and bounded.
- Stop new buys after the account is having a bad day.
- Stop immediately after repeated stop-losses.
- Avoid buying the same stock again right after a stop-loss.
- Do not allow stale risk PASS results to create orders later.
- Block new BUYs in weak market/sector regimes when current market context is clearly negative.
- Block repeated BUYs after recent realized losses, using existing realized P/L records.
- Protect winning positions after the first target sell by exiting the rest near breakeven.
- Block overheated chase buys using latest technical indicators.
- Preserve a minimum cash reserve so the account never spends all buying power.
- Prevent portfolio concentration by limiting open positions and sector exposure.
- Apply the same guardrails to PAPER and REAL mode.

The current BUY sizing is mostly based on `totalAsset * AI recommendedPortfolioWeight`. That can work for opportunity sizing, but it is not enough for capital preservation. BUY quantity should be capped by "how much money can be lost if stopLossPrice is hit."

## Implementation Plan

1. Add risk-guard properties under `cpm.risk.guard`.
   - `max-risk-per-trade-rate`: maximum account loss per BUY, default `0.005` (0.5%).
   - `daily-loss-limit-rate`: stop new BUYs after daily account drawdown, default `0.015` (1.5%).
   - `max-daily-stop-loss-count`: stop new BUYs after this many stop-loss sells today, default `2`.
   - `same-stock-stop-loss-cooldown-hours`: block re-entry after same-stock stop-loss, default `24`.
   - `max-risk-check-age-minutes`: require a fresh PASS result before order creation, default `10`.
   - `market-loss-block-rate`: block BUYs when the stock's market index daily change is at or below this percent, default `-1.5`.
   - `sector-loss-block-rate`: block BUYs when the stock's sector daily change is at or below this percent, default `-2.5`.
   - `realized-loss-cooldown-hours`: lookback window for realized-loss cooldown, default `48`.
   - `max-recent-realized-loss-count`: block all new BUYs after this many recent account-level realized losses, default `3`.
   - `breakeven-protect-buffer-rate`: after first target sell, sell the rest if price falls back to average buy price plus this buffer, default `0.002` (0.2%).
   - `max-buy-rsi14`: block BUY when latest RSI is at or above this value, default `75`.
   - `max-ma20-extension-rate`: block BUY when current price is this much above MA20, default `0.12` (12%).
   - `max-bollinger-upper-extension-rate`: block BUY when current price is this much above Bollinger upper band, default `0.03` (3%).
   - `min-cash-reserve-rate`: keep at least this share of total assets in cash after BUY sizing, default `0.20` (20%).
   - `max-held-position-count`: block new-stock BUY when already holding this many stocks, default `8`.
   - `max-sector-exposure-rate`: block BUY when expected sector exposure exceeds this share of total assets, default `0.35` (35%).

2. Add mapper queries using existing tables only.
   - Count today's `SELL[STOP_LOSS_HIT]` filled/ordered requests.
   - Check same-stock stop-loss within cooldown.

3. Add RiskService BUY guard checks.
   - In both PAPER and REAL modes, check daily drawdown using current account snapshot.
   - Check today's stop-loss count.
   - Check same-stock stop-loss cooldown.
   - Check same-stock realized-loss cooldown.
   - Check account-level recent realized-loss count.
   - Check market/sector regime with `MarketContextService`.
   - Check overheated technical conditions from `stock_indicator_daily`.
   - Check minimum cash reserve before BUY.
   - Check maximum held position count and sector exposure.
   - Keep existing liquidity, confidence, risk/reward, cash, and position checks.

4. Change BUY order sizing.
   - Existing cap remains: `min(totalAsset * recommendedWeight, availableCash)`.
   - Available cash for BUY is reduced by `totalAsset * minCashReserveRate`.
   - New risk cap:
     - `riskBudget = totalAsset * maxRiskPerTradeRate`
     - `lossPerShare = currentPrice - stopLossPrice`
     - `riskQuantity = floor(riskBudget / lossPerShare)`
   - Final quantity = min(weight quantity, risk quantity, cash quantity).
   - If stop loss is invalid/missing, keep existing risk checks blocking invalid direction.

5. Add OrderService freshness gate.
   - Orders require the current trading mode's latest risk check.
   - The result must be `passed=true`.
   - `checked_at` must be within `maxRiskCheckAgeMinutes`.

6. Add winner protection to target/stop monitor.
   - If `TARGET_HIT_1` already happened and `TARGET_HIT_2` has not happened, compare current price with position average buy price.
   - If current price is at or below `averageBuyPrice * (1 + breakevenProtectBufferRate)`, sell the remaining quantity with `BREAKEVEN_PROTECT`.

7. Verify with focused tests.
   - Buy policy caps quantity by stop-loss risk.
   - RiskService blocks after stop-loss count/cooldown where practical.
   - OrderService blocks stale risk PASS before creating or executing an order.
   - Sell policy sells all remaining quantity for `BREAKEVEN_PROTECT`.
   - Compile and run focused tests.

## Files / Changes

- `src/main/java/com/bowon/cpm/common/config/RiskGuardProperties.java`
- `src/main/java/com/bowon/cpm/common/config/PropertiesConfig.java`
- `src/main/java/com/bowon/cpm/order/mapper/OrderRequestMapper.java`
- `src/main/resources/mapper/order/OrderRequestMapper.xml`
- `src/main/java/com/bowon/cpm/feedback/mapper/PortfolioRealizedProfitLossMapper.java`
- `src/main/resources/mapper/feedback/PortfolioRealizedProfitLossMapper.xml`
- `src/main/java/com/bowon/cpm/order/policy/BuyOrderPolicyEngine.java`
- `src/main/java/com/bowon/cpm/order/service/OrderService.java`
- `src/main/java/com/bowon/cpm/risk/service/RiskService.java`
- `src/main/java/com/bowon/cpm/market/service/MarketContextService.java` is reused; no schema change.
- `src/main/resources/application.yml`
- focused tests

## Test Steps

- Run `gradlew test --tests "...BuyOrderPolicyEngineTest" --tests "...RiskServiceTest" --tests "...OrderServiceTest"`.
- Run `gradlew compileJava processResources`.

## Risks / Assumptions

- No DB schema change is required.
- Daily drawdown uses the latest account snapshot. For PAPER, it uses `paper_account_balance`; for REAL, the KIS sync path creates `account_balance` snapshots.
- Existing stop-loss orders are identifiable via `order_request.request_reason LIKE 'SELL[STOP_LOSS_HIT]%'`.
