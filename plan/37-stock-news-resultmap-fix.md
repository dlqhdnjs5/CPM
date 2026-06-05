## Understanding

`POST /api/news/analyze` fails while mapping `StockNewsMapper.findPendingAnalysisTargets`.
`StockNews` has more fields than the pending-analysis query returns, and MyBatis constructor auto-mapping is trying to map by column order.

## Implementation Plan

Add an explicit `resultMap` for `StockNews` so MyBatis maps by column names/properties instead of constructor column order.
Use it for all stock news select queries.

## Files / Changes

- `src/main/resources/mapper/news/StockNewsMapper.xml`
  - Add `StockNewsResultMap`.
  - Switch select queries from `resultType` to `resultMap`.

## Test Steps

- Run `compileJava`.
- Restart app.
- Retry `POST /api/news/analyze?limit=50`.

## Risks / Assumptions

- No DB schema changes.
- Missing AI summary/sentiment columns in pending-analysis query map to null, which is expected.
