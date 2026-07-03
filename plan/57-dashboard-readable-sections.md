## Understanding

The current static dashboard tries to show every portfolio, AI, risk, scheduler, and failure panel in one compact viewport. This makes the text too small and hard to scan during operation.

## Implementation Plan

1. Keep the existing dashboard API and JavaScript element IDs unchanged.
2. Restructure `dashboard.html` into a vertical operations layout with a sticky section menu.
3. Increase typography, spacing, table row height, and list item readability.
4. Split the old dense grid into clear sections: Overview, Actions, Portfolio, AI / Orders, and Operations.
5. Update the static resource test to assert the new layout shell.

## Files / Changes

- `src/main/resources/static/dashboard.html`
  - Add menu anchors and semantic sections.
  - Preserve all existing dynamic IDs used by `dashboard.js`.
- `src/main/resources/static/dashboard.css`
  - Replace compact one-screen grid styling with readable section styling.
  - Add responsive menu behavior for smaller screens.
- `src/test/java/com/bowon/cpm/admin/DashboardStaticResourceTest.java`
  - Update expectations for the menu-based dashboard layout.

## Test Steps

- Run the dashboard static resource test.
- Run the dashboard-related test set if needed.
- Reload `http://localhost:8080/dashboard.html` and verify the page is readable with no browser console errors.

## Risks / Assumptions

- This is a static UI layout change only; no DB or backend API changes are required.
- If the running Spring app serves previously built static resources, `processResources` or an application restart may be needed before the browser reflects the new HTML/CSS.
