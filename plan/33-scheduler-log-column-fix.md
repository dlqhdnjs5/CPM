## Understanding

`scheduler_execution_log` in the database uses `execution_status`, `message`, and `ended_at`.
The current MyBatis mapper uses non-existent columns `status`, `execution_message`, and `finished_at`, causing scheduled jobs to fail.

## Implementation Plan

Update the scheduler execution log mapper SQL to match the existing DB schema.
No DB schema changes are required.

## Files / Changes

- `src/main/resources/mapper/common/SchedulerExecutionLogMapper.xml`
  - `status` -> `execution_status`
  - `execution_message` -> `message`
  - `finished_at` -> `ended_at`
  - populate `duration_ms` when finishing or expiring a run

## Test Steps

- Run `compileJava`.
- Optionally call mapper through the running app after restart.

## Risks / Assumptions

- Existing Java property names remain unchanged.
- `duration_ms` is computed from `started_at` to `NOW()`.
