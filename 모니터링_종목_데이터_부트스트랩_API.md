# 모니터링 종목 데이터 부트스트랩 API

## 1. API 목적

이 API는 새로 모니터링하고 싶은 주식을 시스템에 추가하고, AI가 바로 매매 판단을 할 수 있도록 필요한 데이터를 한 번에 수집하는 기능이다.

단순히 `stock_master`에 종목만 넣는 것이 아니라, 아래 데이터를 함께 채운다.

```text
종목 기본 정보
DART 공시
DART 주요 이벤트
DART 재무 정보
뉴스
뉴스 AI 요약/감성 분석
현재가
일봉
일봉 기술적 지표
```

즉 “이 종목을 앞으로 AI 판단 대상으로 보겠다”는 의미로 호출하는 초기 데이터 준비 API다.

## 2. API 주소

```text
POST /api/stocks/{stockCode}/ai-data/bootstrap
```

예:

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/005930/ai-data/bootstrap?from=2026-03-01&to=2026-06-05&newsDisplay=30&newsAnalyzeLimit=0"
```

## 3. Path Parameter

### stockCode

모니터링 대상으로 추가할 종목 코드.

예:

```text
005930  삼성전자
036930  주성엔지니어링
042700  한미반도체
102710  이엔에프테크놀로지
```

## 4. Query Parameters

### from

DART 공시 조회 시작일.

형식:

```text
yyyy-MM-dd
```

예:

```text
2026-03-01
```

값을 생략하면 기본값은 현재일 기준 약 3개월 전이다.

### to

DART 공시 조회 종료일.

형식:

```text
yyyy-MM-dd
```

예:

```text
2026-06-05
```

값을 생략하면 기본값은 오늘이다.

### keyword

뉴스 검색 키워드.

생략하면 DART `corp_name`을 사용한다.

예:

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/042700/ai-data/bootstrap?keyword=한미반도체"
```

PowerShell에서 한글 keyword를 직접 붙였을 때 400 오류가 나면 URL 인코딩해서 호출한다.

```powershell
$kw = [uri]::EscapeDataString("한미반도체")
curl.exe -X POST "http://localhost:8080/api/stocks/042700/ai-data/bootstrap?keyword=$kw"
```

### newsDisplay

네이버 뉴스 검색 API에서 가져올 뉴스 개수.

기본값:

```text
30
```

예:

```text
newsDisplay=30
```

### newsAnalyzeLimit

뉴스 AI 요약/감성 분석을 몇 건까지 실행할지 정한다.

기본값:

```text
50
```

뉴스 AI 분석은 OpenAI 호출이 포함되어 시간이 오래 걸릴 수 있다. 빠르게 종목 데이터만 먼저 채우고 싶으면 `0`으로 호출한다.

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/005930/ai-data/bootstrap?from=2026-03-01&to=2026-06-05&newsDisplay=30&newsAnalyzeLimit=0"
```

뉴스 요약/감성 분석만 나중에 따로 실행할 수도 있다.

```powershell
curl.exe -X POST "http://localhost:8080/api/news/analyze?limit=20"
```

## 5. 내부 실행 순서

이 API는 내부적으로 `StockDataBootstrapService`를 호출한다.

파일:

```text
src/main/java/com/bowon/cpm/admin/service/StockDataBootstrapService.java
```

실행 순서:

```text
1. stockMasterUpsert
2. dartDisclosuresFetch
3. dartMajorEventsClassify
4. dartFinancialsFetch
5. newsFetch
6. newsAnalyze
7. currentQuoteFetch
8. dailyPricesFetch
9. dailyIndicatorsCalculate
```

## 6. 단계별 저장 테이블

### stockMasterUpsert

종목 기본 정보를 저장 또는 갱신한다.

대상 테이블:

```text
stock_master
dart_corp_code
```

### dartDisclosuresFetch

DART 공시 목록을 수집한다.

대상 테이블:

```text
dart_disclosure
```

### dartMajorEventsClassify

수집된 DART 공시 중 투자 판단에 중요한 이벤트를 분류한다.

대상 테이블:

```text
dart_major_event
```

예:

```text
자기주식취득
대표이사변경
타인에대한채무보증결정
주식등의대량보유상황보고서
연결재무제표기준영업(잠정)실적
감사보고서제출
기업가치제고계획
```

### dartFinancialsFetch

DART 재무제표 데이터를 수집한다.

대상 테이블:

```text
dart_financial_statement
dart_financial_account
```

### newsFetch

네이버 뉴스 검색 API로 종목 뉴스를 수집한다.

대상 테이블:

```text
stock_news
```

주의:

```text
stock_news.summary는 네이버 검색 API의 description이다.
기사 본문 전체 요약이 아니라 검색 결과 스니펫이므로 끝이 ... 로 잘릴 수 있다.
```

### newsAnalyze

수집된 뉴스에 대해 AI 요약과 감성 분석을 수행한다.

대상 테이블:

```text
news_ai_summary
news_sentiment
```

AI 판단 프롬프트에서는 `stock_news` 조회 시 `news_ai_summary`를 조인해서 사용한다.

```text
news_ai_summary.summary가 있으면 AI 요약 사용
없으면 stock_news.summary 사용
```

### currentQuoteFetch

현재가를 조회하고 저장한다.

대상 테이블:

```text
stock_realtime_quote
```

### dailyPricesFetch

일봉 데이터를 수집한다.

대상 테이블:

```text
stock_price_daily
```

### dailyIndicatorsCalculate

수집된 일봉 기준으로 기술적 지표를 계산한다.

대상 테이블:

```text
stock_indicator_daily
```

## 7. 응답 예시

```json
{
  "success": true,
  "message": "AI data bootstrap completed",
  "data": {
    "inputCode": "036930",
    "stockCode": "036930",
    "stockName": "주성엔지니어링",
    "corpCode": "00252135",
    "keyword": "주성엔지니어링",
    "from": "2026-03-01",
    "to": "2026-06-05",
    "allSucceeded": true,
    "steps": [
      {
        "stepName": "stockMasterUpsert",
        "success": true,
        "count": 1,
        "errorMessage": null,
        "elapsedMs": 18
      }
    ]
  }
}
```

## 8. 응답에서 봐야 할 값

### allSucceeded

모든 단계가 성공했는지 나타낸다.

```text
true  = 모든 단계 성공
false = 일부 단계 실패
```

### steps

각 단계별 성공/실패와 저장 건수를 확인한다.

중요하게 볼 값:

```text
stepName
success
count
errorMessage
elapsedMs
```

예를 들어 `newsAnalyze`만 실패하고 나머지가 성공할 수 있다.

이 경우 뉴스 원문, DART, 시세, 일봉, 지표는 들어갔고 뉴스 AI 분석만 별도 재시도하면 된다.

```powershell
curl.exe -X POST "http://localhost:8080/api/news/analyze?limit=20"
```

## 9. 자주 쓰는 호출 예시

### 삼성전자 추가

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/005930/ai-data/bootstrap?from=2026-03-01&to=2026-06-05&newsDisplay=30&newsAnalyzeLimit=0"
```

### 주성엔지니어링 추가

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/036930/ai-data/bootstrap?from=2026-03-01&to=2026-06-05&newsDisplay=30&newsAnalyzeLimit=0"
```

### 한미반도체 추가

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/042700/ai-data/bootstrap?from=2026-03-01&to=2026-06-05&newsDisplay=30&newsAnalyzeLimit=0"
```

### 이엔에프테크놀로지 추가

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/102710/ai-data/bootstrap?from=2026-03-01&to=2026-06-05&newsDisplay=30&newsAnalyzeLimit=0"
```

## 10. 호출 후 확인 API

### 뉴스 확인

```powershell
curl.exe "http://localhost:8080/api/stocks/005930/news?limit=20"
```

### DART 공시 확인

```powershell
curl.exe "http://localhost:8080/api/stocks/005930/disclosures?limit=20"
```

### DART 주요 이벤트 확인

```powershell
curl.exe "http://localhost:8080/api/dart/005930/events?limit=20"
```

### DART 재무 확인

```powershell
curl.exe "http://localhost:8080/api/dart/005930/financials"
```

### 일봉 확인

```powershell
curl.exe "http://localhost:8080/api/stocks/005930/prices/daily?limit=30"
```

### 현재가 확인

```powershell
curl.exe "http://localhost:8080/api/stocks/005930/quote"
```

## 11. 운영상 권장 방식

처음 모니터링 종목을 추가할 때는 빠르게 데이터만 채우기 위해 `newsAnalyzeLimit=0`으로 호출한다.

```powershell
curl.exe -X POST "http://localhost:8080/api/stocks/005930/ai-data/bootstrap?from=2026-03-01&to=2026-06-05&newsDisplay=30&newsAnalyzeLimit=0"
```

그 다음 뉴스 AI 분석은 별도로 나눠서 실행한다.

```powershell
curl.exe -X POST "http://localhost:8080/api/news/analyze?limit=20"
```

이 방식이 좋은 이유:

```text
OpenAI 뉴스 분석 시간이 길어져서 bootstrap API가 타임아웃되는 상황을 줄일 수 있다.
DART, 뉴스 원문, 일봉, 지표 데이터는 먼저 안정적으로 들어간다.
뉴스 AI 분석은 필요한 만큼 나눠서 돌릴 수 있다.
```

## 12. 주의사항

이 API가 성공했다고 해서 바로 주문이 실행되는 것은 아니다.

이 API의 목적:

```text
모니터링 종목 추가
AI 판단용 데이터 준비
```

실제 주문 흐름:

```text
AiDecisionScheduler
→ ai_decision 생성
→ OrderExecutionScheduler
→ risk_check_result 생성
→ order_request 생성
→ PAPER 또는 KIS 주문 실행
```

따라서 이 API는 주문 API가 아니라, AI가 판단할 수 있도록 데이터를 준비하는 API다.
