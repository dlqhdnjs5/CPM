# Plan 27: 뉴스 요약/감성 분석

## Understanding

`stock_news` 수집은 존재하지만 `news_ai_summary`, `news_sentiment`를 채우는 분석 파이프라인이 부족하다.
AI 판단 품질을 높이려면 최근 뉴스의 핵심 요약과 감성/영향 점수를 구조화해서 저장하고 프롬프트에 반영해야 한다.

## Implementation Plan

- `news_ai_summary`, `news_sentiment` 도메인/매퍼를 추가한다.
- 아직 분석되지 않은 `stock_news`를 조회한다.
- OpenAI text completion으로 뉴스 요약/감성 JSON을 생성한다.
- JSON 파싱 성공 시:
  - `news_ai_summary` 저장
  - `news_sentiment` 저장
- AI 판단 프롬프트에서 기존 `stock_news.summary` 대신 AI 요약이 있으면 우선 사용하고, 감성/영향 점수를 함께 노출한다.
- 장 시작 전/마감 후 분석 스케줄러와 수동 Admin API를 추가한다.

## Files / Changes

- `news/domain/NewsAiSummary.java`
- `news/domain/NewsSentiment.java`
- `news/mapper/NewsAiSummaryMapper.java/.xml`
- `news/mapper/NewsSentimentMapper.java/.xml`
- `news/service/NewsAnalysisService.java`
- `scheduler/NewsAnalysisScheduler.java`
- `news/domain/StockNews.java`, `StockNewsMapper.xml`
- `admin/NewsController.java`
- 테스트: 분석 JSON 파서/서비스 단위 테스트

## Test Steps

- OpenAI mock 응답으로 summary/sentiment 저장 검증
- 잘못된 JSON 응답은 해당 뉴스만 실패 처리하고 전체 배치가 멈추지 않는지 검증
- AI 프롬프트에 AI summary/sentiment가 반영되는지 검증
- 전체 테스트 `./gradlew.bat test`

## Risks / Assumptions

- OpenAI 응답은 text completion이므로 JSON extraction 방어 로직을 둔다.
- 뉴스 감성은 POSITIVE/NEUTRAL/NEGATIVE 중 하나로 정규화한다.
- 기존 스키마를 사용하며 신규 테이블은 추가하지 않는다.
