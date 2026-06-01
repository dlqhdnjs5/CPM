# Plan: 5단계 - OpenAI AI 판단 JSON 생성 및 ai_decision 저장

## Understanding

목표:
1. OpenAI API를 WebClient로 직접 호출 (Responses API, json_schema 기반 구조화 응답)
2. 종목 분석 데이터(일봉, 뉴스, 공시)를 수집해 프롬프트 생성
3. AI 응답 JSON 파싱 → ai_decision, ai_decision_factor 저장
4. 원문 응답 → ai_decision_raw_response 저장
5. 프롬프트 → ai_prompt_log 저장
6. 파싱 실패 시 graceful 처리

---

## Implementation Plan

### 1. OpenAI 클라이언트
- `OpenAiDecisionClient` — POST /v1/responses (Responses API, json_schema)
- `OpenAiResponse` DTO — 응답 구조
- `OpenAiProperties` — base-url, api-key, model

### 2. 프롬프트 빌더
- `AiDecisionPromptBuilder`
  - buildSystemPrompt()
  - buildUserPrompt(stockCode, 일봉, 뉴스, 공시 데이터)

### 3. AI 판단 JSON DTO
- `AiTradeDecisionJson` — OpenAI JSON 응답 파싱용

### 4. AI 판단 파서
- `AiDecisionParser` — JSON 텍스트 → AiTradeDecisionJson

### 5. AI 도메인 + Mapper
- `AiPromptLog`, `AiDecisionRawResponse`, `AiDecision`, `AiDecisionFactor`
- 각 Mapper + XML

### 6. AiDecisionService
- generateDecision(stockCode) — 전체 플로우 실행
  1. 종목 데이터 수집 (일봉 20일, 뉴스 10건, 공시 5건)
  2. ai_prompt_log 저장
  3. OpenAI API 호출
  4. ai_decision_raw_response 저장
  5. JSON 파싱
  6. ai_decision 저장
  7. ai_decision_factor 저장

### 7. Admin API
- POST /api/ai/decisions/{stockCode} — AI 판단 생성
- GET  /api/ai/decisions — 판단 목록 조회
- GET  /api/ai/decisions/{id} — 판단 상세 조회

---

## Files / Changes

```
ai/
  client/OpenAiDecisionClient.java
  client/dto/OpenAiResponse.java
  client/OpenAiProperties.java
  prompt/AiDecisionPromptBuilder.java
  parser/AiDecisionParser.java
  domain/AiTradeDecisionJson.java
  domain/AiPromptLog.java
  domain/AiDecisionRawResponse.java
  domain/AiDecision.java
  domain/AiDecisionFactor.java
  mapper/AiPromptLogMapper.java
  mapper/AiDecisionRawResponseMapper.java
  mapper/AiDecisionMapper.java
  mapper/AiDecisionFactorMapper.java
  service/AiDecisionService.java

admin/
  AiController.java

resources/mapper/ai/
  AiPromptLogMapper.xml
  AiDecisionRawResponseMapper.xml
  AiDecisionMapper.xml
  AiDecisionFactorMapper.xml
```

---

## Test Steps

1. DART corp_code sync, 일봉/뉴스 수집 완료된 상태에서
2. `POST /api/ai/decisions/005930` 호출
3. ai_prompt_log, ai_decision_raw_response, ai_decision, ai_decision_factor 저장 확인
4. `GET /api/ai/decisions?stockCode=005930` 조회

---

## Risks / Assumptions

- OpenAI Responses API(/v1/responses) 사용 — Chat Completions(/v1/chat/completions)와 다름
- json_schema strict:true 사용 — additionalProperties:false 필수
- AI 응답 파싱 실패 시 ai_decision 생성 안 하고 raw_response만 저장
- ai_decision_factor의 factor_type은 TECHNICAL, NEWS, DART, FUNDAMENTAL, SUPPLY_DEMAND

