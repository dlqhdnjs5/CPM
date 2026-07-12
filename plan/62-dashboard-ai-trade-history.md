## Understanding

The current dashboard summary shows only the latest 12 AI decisions. That is useful as a quick snapshot, but it does not answer trade-flow questions such as "BUY was created, did risk pass, and was an order created?"

The requested dashboard view should show the latest 50 BUY/SELL decisions as a compact history, without making the page too long.

## Implementation Plan

1. Add a dashboard-specific AI trade history query.
   - Source table: `ai_decision`
   - Include only `BUY` / `SELL` by default.
   - Join the latest `risk_check_result` per decision.
   - Join the latest `order_request` per decision.
   - Limit to a safe bounded value, default 50.

2. Add an API endpoint.
   - `GET /api/dashboard/ai-trade-history?decision=ALL&limit=50`
   - `decision` supports `ALL`, `BUY`, `SELL`.
   - Clamp `limit` to avoid accidentally rendering too much.

3. Add a dashboard section.
   - Keep the existing recent 12 "AI Decisions" panel as a snapshot.
   - Add "AI Trade History" as a compact table below it.
   - Default to BUY/SELL only.
   - Add segmented filter buttons: ALL / AI BUY / AI SELL / BUY FILLED / SELL FILLED.
   - Rows show time, stock, decision, confidence, risk result, order status.
   - Clicking a row expands reason/fail reason/order amount details.

5. Add filled-order filters.
   - `BUY_FILLED`: decisions that have a latest filled BUY order.
   - `SELL_FILLED`: decisions that have a latest filled SELL order.
   - This must look at `order_request.order_side/order_status`, not only `ai_decision.decision`, because trigger-based sells are tied to the original BUY decision.

4. Verification.
   - Compile Java.
   - If practical, use the existing local dashboard endpoint after app restart.

## Files / Changes

- `src/main/java/com/bowon/cpm/dashboard/domain/DashboardAiTradeHistoryItem.java`
- `src/main/java/com/bowon/cpm/dashboard/service/DashboardService.java`
- `src/main/java/com/bowon/cpm/admin/DashboardController.java`
- `src/main/java/com/bowon/cpm/ai/mapper/AiDecisionMapper.java`
- `src/main/resources/mapper/ai/AiDecisionMapper.xml`
- `src/main/resources/static/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/dashboard.css`

## Test Steps

- Run `gradlew compileJava processResources`.
- Manually check `/api/dashboard/ai-trade-history?limit=50`.
- Refresh `/dashboard.html` and confirm the new history table renders without stretching the page too much.

## Risks / Assumptions

- No DB schema change is needed.
- The "latest risk/order" joins use the most recent row per `ai_decision_id`.
- Existing dashboard text has some encoding-corrupted Korean labels; this change will use ASCII/English labels to avoid worsening encoding issues.
