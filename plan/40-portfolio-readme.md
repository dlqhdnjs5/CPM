# Plan 40: Portfolio README Rewrite

## Understanding

Public portfolio repository 기준으로 README를 새로 작성한다.
핵심은 단순 자동매매 서버가 아니라, AI 판단을 데이터/리스크/주문 파이프라인 안에 넣고 검증 가능하게 만든 프로젝트라는 점을 보여주는 것이다.

## Implementation Plan

1. 기존 README를 포트폴리오용 소개 문서로 교체한다.
2. AI 활용 방식은 "AI에게 맡김"이 아니라 "계획, 제약, 검증, 기록을 통해 AI를 다룸"으로 설명한다.
3. Mermaid로 모듈 구조와 AI 판단부터 주문까지의 흐름을 표현한다.
4. 기술 스택, 모델 설정, PAPER/REAL 모드, 리스크 게이트, 피드백 루프를 요약한다.

## Files / Changes

- `README.md`: 프로젝트 소개, 구조, AI 활용 방식, 아키텍처 다이어그램, 매매 흐름, 기술적 포인트 작성

## Test Steps

- Markdown 문법과 Mermaid 코드 블록 확인
- 민감정보가 포함되지 않았는지 문자열 검색

## Risks / Assumptions

- README는 구현 상태를 과장하지 않고 현재 코드에 있는 구조를 기준으로 작성한다.
- 실제 투자 수익을 보장하는 문구는 넣지 않는다.
