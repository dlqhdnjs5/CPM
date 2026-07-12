## Understanding

The Active Stocks dashboard section is confusing because it exposes three inputs and English labels. The user needs a simpler screen for adding stocks that AI should analyze.

## Implementation Plan

1. Rename the Active Stocks section to a clearer Korean label.
2. Remove the Market input from the dashboard form.
3. Keep only stock code and stock name inputs.
4. Keep the backend API unchanged; omit `marketType` from the UI request so the server defaults it.
5. Update the static resource test and run it.

## Files / Changes

- `src/main/resources/static/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/test/java/com/bowon/cpm/admin/DashboardStaticResourceTest.java`

## Test Steps

- Run `DashboardStaticResourceTest`.

## Risks / Assumptions

- Existing stock cards may still show `UNKNOWN` when market type is unknown. That is acceptable for now, but the primary label will remain the stock name.
