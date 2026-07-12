# Plan 56: Static Dashboard MVP

## Understanding

The system needs a first operational dashboard that explains the current PAPER trading state, recent AI/risk/order flow, and failures without adding React or a frontend build pipeline. SSE can come later. The first version should be plain static HTML/CSS/JavaScript served by Spring Boot and backed by a compact dashboard summary API.

## Implementation Plan

1. Add a dashboard summary API.
   - `GET /api/dashboard/summary`
   - Include trading mode, enabled flag, account number mask, PAPER balance, PAPER positions, recent AI decisions, recent orders, recent scheduler logs, and recent API failures.
2. Add mapper queries for recent operational logs if existing mappers only support inserts.
3. Add static dashboard files.
   - `src/main/resources/static/dashboard.html`
   - `src/main/resources/static/dashboard.css`
   - `src/main/resources/static/dashboard.js`
4. Add manual action buttons for the workflows already exposed by APIs.
   - Initialize PAPER balance
   - Refresh dashboard
   - Generate AI decision for a stock code
   - Run risk check for an AI decision ID
   - Create order request for an AI decision ID
5. Add tests.
   - Controller test for dashboard summary endpoint.
   - Static resource sanity test for dashboard files.

## Files / Changes

- New `dashboard` package for DTO/service/controller.
- Extend log mappers with read-only recent queries.
- Add static dashboard assets.
- Add focused tests under `src/test/java`.

## Test Steps

- Run dashboard controller/static tests.
- Run `compileJava` and `compileTestJava`.
- If the app is running, open `http://localhost:8080/dashboard.html` and verify the page renders.

## Risks / Assumptions

- This is an operational MVP, not a full analytics UI.
- Polling is acceptable for now; SSE/WebSocket can reuse the same summary model later.
- The dashboard is intentionally dense and utilitarian, optimized for quickly answering why PAPER trading did or did not act.
