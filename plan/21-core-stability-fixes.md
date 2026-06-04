# Plan 21: 기존 결함 보정

## Understanding

BUY/SELL 확장 전에 기존 운영 결함을 먼저 고친다.

## Implementation Plan

- `AccountBalanceMapper.findLatestByAccountNo`가 최근 2건 이상 반환하도록 SQL을 수정한다.
- `FeedbackService.saveDailyProfitLoss()`가 최신 잔고와 이전 잔고를 안정적으로 비교하도록 유지/보정한다.
- AI raw response 파싱 성공 시 `is_parsed=true`, `parse_error=null`로 업데이트한다.
- 오래된 `scheduler_execution_log.status='RUNNING'`을 timeout 처리하여 스케줄러 고착을 막는다.

## Files / Changes

- `AccountBalanceMapper.xml`
- `AiDecisionRawResponseMapper.java/.xml`
- `AiDecisionPersistService.java`
- `AiDecisionService.java`
- `SchedulerExecutionLogMapper.java/.xml`
- `SchedulerLogSupport.java`

## Test Steps

- AI 파싱 성공 시 raw response parsed 상태가 업데이트되는지 단위/통합 흐름 확인.
- stale RUNNING 로그가 FAILED로 정리되는지 mapper/service 수준 확인.
- `./gradlew.bat test` 전체 통과.

## Risks / Assumptions

- 스케줄러 stale timeout 기본값은 60분으로 둔다.
