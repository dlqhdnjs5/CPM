## Understanding

매크로 뉴스 수집은 장 시작 직전 8시대에 몰릴 필요가 없다.
연준, 한국은행, 환율, 미국채, 해외시장 뉴스는 한국 시간 새벽에도 충분히 쌓이므로 06시대에 먼저 수집하고, OpenAI 분석은 별도 뉴스 분석 스케줄러가 제한 건수로 처리하는 편이 안정적이다.

## Implementation Plan

1. `MacroNewsCollectScheduler`를 추가한다.
2. 평일 06:10에 매크로 뉴스 1차 수집을 실행한다.
3. 평일 15:00에 장중 매크로 뉴스 갱신을 실행한다.
4. 스케줄러에서는 `analyze=false` 방식으로 수집만 수행한다.
5. 기존 `NewsAnalysisScheduler`에 평일 06:30 실행을 추가한다.
6. 테스트로 스케줄러가 서비스 호출, 성공/실패 로그, 주말/중복 실행 스킵을 처리하는지 확인한다.

## Files / Changes

- `src/main/java/com/bowon/cpm/scheduler/MacroNewsCollectScheduler.java`
- `src/main/java/com/bowon/cpm/scheduler/NewsAnalysisScheduler.java`
- `src/test/java/com/bowon/cpm/scheduler/MacroNewsCollectSchedulerTest.java`
- `src/test/java/com/bowon/cpm/scheduler/NewsAnalysisSchedulerTest.java`

## Test Steps

1. MacroNewsCollectScheduler 단위 테스트
2. NewsAnalysisScheduler 단위 테스트
3. 관련 테스트만 Gradle로 실행

## Risks / Assumptions

- DB 스키마 변경 없음.
- 매크로 수집 스케줄러는 OpenAI 분석을 직접 수행하지 않음.
- 기존 `NewsAnalysisScheduler`가 매크로 뉴스와 종목 뉴스를 함께 pending 기준으로 분석한다.
