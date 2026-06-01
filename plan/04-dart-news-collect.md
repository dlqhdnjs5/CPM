# Plan: 4단계 - OpenDART 공시 + 네이버 뉴스 수집

## Understanding

목표:
1. OpenDART API에서 corp_code 수집 및 종목코드 매핑 저장
2. OpenDART 공시 목록 조회 및 dart_disclosure 저장
3. 네이버 뉴스 검색 API로 종목별 뉴스 수집 및 stock_news 저장
4. origin_url_hash(SHA-256) 기반 중복 뉴스 제거

---

## Implementation Plan

### 1. DART corp_code 수집
- `DartCorpCodeClient` — corpCode.xml ZIP 다운로드 + 파싱
- `DartCorpCodeZipParser` — ZIP 압축 해제 + XML 파싱
- `DartCorpCodeMapper` + XML — dart_corp_code 배치 저장
- `DartCorpCode` domain

### 2. DART 공시 목록 수집
- `DartDisclosureClient` — 공시 목록 조회 (`/api/list.json`)
- `DartDisclosureResponse` DTO
- `DartDisclosureMapper` + XML — dart_disclosure INSERT IGNORE
- `DartDisclosure` domain
- `DartService` — corpCode 조회 + 공시 수집 통합

### 3. 네이버 뉴스 수집
- `NaverNewsClient` — 뉴스 검색 (`/v1/search/news.json`)
- `NaverNewsResponse` DTO
- `StockNewsMapper` + XML — stock_news INSERT IGNORE (origin_url_hash UK)
- `StockNews` domain
- `NewsUtils` — HTML 태그 제거, SHA-256 해시
- `NewsCollectService` — 종목별 뉴스 수집

### 4. external_api_call_log 저장
- `ExternalApiCallLog` domain
- `ExternalApiCallLogMapper` + XML
- DART, Naver 호출 시 로그 저장

### 5. Admin API
- `POST /api/dart/{stockCode}/disclosures/fetch` — 공시 수집
- `GET  /api/stocks/{stockCode}/disclosures` — 공시 조회
- `POST /api/news/{stockCode}/fetch` — 뉴스 수집
- `GET  /api/stocks/{stockCode}/news` — 뉴스 조회

---

## Files / Changes

```
dart/
  client/DartCorpCodeClient.java
  client/DartCorpCodeZipParser.java
  client/DartDisclosureClient.java
  client/dto/DartDisclosureResponse.java
  domain/DartCorpCode.java
  domain/DartDisclosure.java
  mapper/DartCorpCodeMapper.java
  mapper/DartDisclosureMapper.java
  service/DartService.java

news/
  client/NaverNewsClient.java
  client/dto/NaverNewsResponse.java
  domain/StockNews.java
  mapper/StockNewsMapper.java
  service/NewsCollectService.java
  util/NewsUtils.java

common/
  domain/ExternalApiCallLog.java
  mapper/ExternalApiCallLogMapper.java

admin/
  DartController.java
  NewsController.java

resources/mapper/
  dart/DartCorpCodeMapper.xml
  dart/DartDisclosureMapper.xml
  news/StockNewsMapper.xml
  common/ExternalApiCallLogMapper.xml
```

---

## Test Steps

1. `POST /api/dart/005930/disclosures/fetch` → dart_disclosure 저장 확인
2. `GET  /api/stocks/005930/disclosures` → 공시 목록 조회
3. `POST /api/news/005930/fetch` → stock_news 저장 확인
4. `GET  /api/stocks/005930/news` → 뉴스 목록 조회
5. 중복 수집 시 INSERT IGNORE 동작 확인

---

## Risks / Assumptions

- DART corpCode.xml은 ZIP 형태로 내려옴 → ZipInputStream으로 파싱
- DART API key는 application-local.yml에 직접 입력 필요
- 네이버 뉴스 title/description에 HTML 태그 포함 → 정제 필요
- stock_news.origin_url_hash (SHA-256) UK로 중복 제거
- DART status "000" = 성공, 나머지 = 실패

