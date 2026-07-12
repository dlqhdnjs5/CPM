## Understanding

`dart_disclosure` contains disclosures for all target stocks, but `dart_major_event` only has Samsung Electronics rows. The classifier in `DartMajorEventService` uses corrupted Korean keyword literals and a narrow event list, so normal Korean disclosure titles are not detected.

## Implementation Plan

1. Replace corrupted major-event keywords with valid Korean disclosure keywords.
2. Expand event types to cover representative/significant disclosures used by current target stocks.
3. Keep the existing table and mapper contracts unchanged.
4. Add a service unit test that verifies matching disclosures are inserted and routine disclosures are ignored.

## Files / Changes

- `src/main/java/com/bowon/cpm/dart/service/DartMajorEventService.java`
- `src/test/java/com/bowon/cpm/dart/service/DartMajorEventServiceTest.java`

## Test Steps

- Run focused tests for `DartMajorEventServiceTest`.
- Run existing bootstrap/news related tests to ensure no regressions.
- Re-run major-event fetch/classification API for affected stock codes.

## Risks / Assumptions

- `dart_major_event` remains a filtered important-event table, not a copy of all disclosures.
- OpenAI summary failures should not block event insertion because the service already falls back to the report title.
