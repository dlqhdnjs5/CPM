## Understanding

AI 결정 프롬프트에 현재가 기준, HOLD 비중, null 데이터 추론 금지, 손익비 기준, 큰 현재가 변동 해석 규칙을 추가한다.
응답 필드는 현재 파서/DB가 기대하는 스키마를 깨지 않도록 "정해진 응답 스키마 밖 필드 추가 금지"로 제한한다.

## Implementation Plan

1. `AiDecisionPromptBuilder.buildSystemPrompt()`의 Decision rules에 guardrail 문구를 추가한다.
2. 기존 응답 스키마와 충돌하는 "exactly these fields" 표현은 사용하지 않는다.
3. 테스트에서 주요 guardrail 문구가 시스템 프롬프트에 포함되는지 검증한다.

## Files / Changes

- `src/main/java/com/bowon/cpm/ai/prompt/AiDecisionPromptBuilder.java`
- `src/test/java/com/bowon/cpm/ai/prompt/AiDecisionPromptBuilderTest.java`

## Test Steps

1. `AiDecisionPromptBuilderTest` 실행

## Risks / Assumptions

- DB 스키마 변경 없음.
- AI 응답 JSON 파서 스키마 변경 없음.
