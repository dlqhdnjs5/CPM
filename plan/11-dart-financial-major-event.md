# Plan: 11단계 - DART 재무제표 + 주요 이벤트 수집 + 공시 요약

## Understanding

현재 DART에서 공시 목록(dart_disclosure)만 수집하고 있음.
AI 판단 품질 향상을 위해 아래 3가지를 추가 구현한다.

1. **재무제표 수집** — 매출/영업이익/순이익 등 핵심 재무 데이터
2. **주요 이벤트 수집** — 유상증자, 배당, 단일판매계약 상세 정보
3. **공시/재무 요약** — OpenAI로 요약 → AI 프롬프트에 포함

---

## Implementation Plan

### 1. DART 재무제표 수집

DART API: `GET /api/fnlttSinglAcnt.json`

파라미터:
- `corp_code` — dart_corp_code에서 조회
- `bsns_year` — 2024, 2025
- `reprt_code` — 11011 (사업보고서), 11012 (반기), 11013 (1분기), 11014 (3분기)

수집 항목 (account_nm 기준 필터):
```
매출액 / 영업이익 / 당기순이익 / 자산총계 / 부채총계 / 자본총계
```

저장: `dart_financial_statement`
UK: `corp_code + business_year + report_code + statement_type + account_name`

---

### 2. DART 주요 이벤트 수집

공시 목록(dart_disclosure)에서 report_name 키워드 매칭 후
종류별 DART 정형 API 호출

| 키워드 | event_type | DART API |
|--------|-----------|---------|
| 유상증자결정 | CAPITAL_INCREASE | /api/piicDecsn.json |
| 현금배당결정 | DIVIDEND | /api/cashDvdnDecsn.json |
| 단일판매·공급계약 | SINGLE_CONTRACT | /api/sng_cnt_decsn.json (TODO 확인) |
| 감자결정 | CAPITAL_DECREASE | /api/crscDecsn.json |
| 최대주주변경 | MAJOR_SHAREHOLDER | /api/cstLdtChDecsn.json |

dart_major_event 저장:
- `event_type`, `event_date`, `event_title`, `event_summary`, `raw_json`

---

### 3. 공시/재무 OpenAI 요약

**재무 요약**: dart_financial_statement 데이터 → OpenAI → 텍스트 요약
```
2025년 매출 9.8조 (전년대비 +15%), 영업이익 1.2조 (+35%), 부채비율 42%
```

**공시 요약**: dart_major_event raw_json → OpenAI → event_summary 업데이트
```
유상증자결정: 신주 1억주 (5,000원), 주주배정, 납입일 2026-06-01
→ 요약: "약 5,000억 규모 주주배정 유상증자. 희석 우려 있음."
```

---

### 4. AI 프롬프트 섹션 추가

AiDecisionPromptBuilder에 2개 섹션 추가:

```
## 재무 요약 (최근 2개년)
...

## ⚠️ 주요 이벤트 (최근 6개월)
...
```

---

## Files / Changes

```
dart/
  client/
    DartFinancialClient.java       (재무제표 API 호출)
    DartMajorEventClient.java      (주요 이벤트 API 호출)
  domain/
    DartFinancialStatement.java
    DartMajorEvent.java
  mapper/
    DartFinancialStatementMapper.java
    DartMajorEventMapper.java
  service/
    DartFinancialService.java      (수집 + 저장 + OpenAI 요약)
    DartMajorEventService.java     (이벤트 분류 + 저장 + OpenAI 요약)

resources/mapper/dart/
  DartFinancialStatementMapper.xml
  DartMajorEventMapper.xml

ai/prompt/AiDecisionPromptBuilder.java  (재무요약 + 주요이벤트 섹션 추가)
ai/service/AiDecisionService.java       (재무/이벤트 데이터 조회 후 프롬프트 전달)

admin/DartController.java  (기존에 재무/이벤트 수집 API 추가)
```

---

## Test Steps

```
# 1. corp_code 동기화 (이미 됨)
POST /api/dart/corp-codes/sync

# 2. 재무제표 수집
POST /api/dart/005930/financials/fetch

# 3. 주요 이벤트 수집
POST /api/dart/005930/events/fetch

# 4. AI 판단 생성 (프롬프트에 재무/이벤트 포함됐는지 확인)
POST /api/ai/decisions/005930
```

---

## Risks / Assumptions

- DART API 호출 제한: 하루 1만 건 (넉넉함)
- `corp_code`는 dart_corp_code에서 stock_code로 조회
- 재무제표는 분기별로 중복 수집 방지 (UK로 처리)
- 주요 이벤트 API endpoint 중 일부는 DART 공식 문서 확인 필요 ("찾아서 넣으세요" 처리)
- OpenAI 요약은 비용 절감을 위해 gpt-4.1-mini 사용
- dart_major_event.raw_json 컬럼 존재 여부 확인 필요

