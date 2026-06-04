---
applyTo: '**'
description: 'DB 스키마 참조 및 변경 규칙'
---

# DB 스키마 지시문

## 접속 정보
- Host: localhost:3306
- Database: cpm
- Username: givememoney
- Password: givememoney1234
- Charset: utf8mb4 (utf8mb4_0900_ai_ci)

## 핵심 규칙

1. **DB 스키마 임의 변경 절대 금지** - 컬럼 추가/삭제/타입 변경/인덱스 변경은 반드시 사용자 승인 후 진행
2. 코드는 현재 스키마 기준으로 작성한다
3. 스키마와 코드가 불일치하면 사용자에게 보고한다

## 테이블 목록 (8개 도메인)

### 1. 종목/시장 데이터
- `stock_master` (PK: stock_code) - 종목 기본 정보
- `stock_price_daily` (PK: id, UK: stock_code+trade_date) - 일봉
- `stock_price_minute` (PK: id, UK: stock_code+trade_datetime+interval_minute) - 분봉
- `stock_realtime_quote` (PK: id) - 실시간 현재가
- `stock_orderbook` (PK: id) - 호가
- `stock_indicator_daily` (PK: id, UK: stock_code+trade_date) - 일봉 기술적 지표
- `stock_indicator_minute` (PK: id, UK: stock_code+trade_datetime+interval_minute) - 분봉 기술적 지표
- `market_index` (PK: id, UK: index_code+trade_date) - 시장 지수

### 2. OpenDART 데이터
- `dart_corp_code` (PK: corp_code) - 기업 고유번호
- `dart_company_overview` (PK: id, UK: corp_code) - 기업 개황
- `dart_disclosure` (PK: id, UK: receipt_no) - 공시 목록
- `dart_financial_statement` (PK: id, UK: corp_code+business_year+report_code+statement_type+account_name) - 재무제표
- `dart_financial_account` (PK: id, UK: corp_code+business_year+report_code) - 주요 재무 계정
- `dart_major_event` (PK: id, UK: receipt_no+event_type) - 주요 공시 이벤트

### 3. 뉴스 데이터
- `news_keyword` (PK: id, UK: stock_code+keyword) - 뉴스 검색 키워드
- `stock_news` (PK: id, UK: origin_url_hash) - 종목 뉴스 (중복제거: SHA-256 해시)
- `news_ai_summary` (PK: id, UK: news_id) - 뉴스 AI 요약
- `news_sentiment` (PK: id, UK: news_id) - 뉴스 감성 분석

### 4. AI 판단 데이터
- `ai_prompt_log` (PK: id) - AI 프롬프트 요청 로그
- `ai_decision_raw_response` (PK: id) - AI 원문 응답
- `ai_decision` (PK: id) - AI 매매 판단 (decision: BUY/SELL/HOLD)
- `ai_decision_factor` (PK: id) - AI 판단 근거 상세
- `ai_feedback` (PK: id, UK: ai_decision_id+evaluation_type) - AI 판단 성과 피드백

### 5. 계좌/포트폴리오
- `account_balance` (PK: id) - 계좌 잔고 스냅샷
- `portfolio_position` (PK: id, UK: account_no+stock_code) - 현재 보유 종목
- `portfolio_snapshot` (PK: id) - 포트폴리오 스냅샷
- `portfolio_profit_loss` (PK: id, UK: account_no+stock_code+base_date+evaluation_type) - 수익률
- `portfolio_realized_profit_loss` (PK: id, UK: order_execution_id) - SELL 체결별 실현손익

### 6. 주문/체결
- `order_request` (PK: id, UK: idempotency_key) - 주문 요청
  - 컬럼: id, ai_decision_id, account_no, broker_type(KIS), stock_code, order_side, order_type, order_price, order_quantity, order_amount, order_status(READY), idempotency_key, request_reason, broker_order_no, **requested_at**, updated_at
  - ⚠️ `created_at` 없음 → `requested_at` 사용
- `order_execution` (PK: id) - 주문 체결 이력
- `order_cancel_request` (PK: id) - 주문 취소 요청
- `order_status_history` (PK: id) - 주문 상태 변경 이력

### 7. 리스크/전략
- `risk_policy_config` (PK: id, UK: policy_code) - 리스크 정책 설정
- `risk_check_result` (PK: id) - 리스크 검증 결과
- `strategy_config` (PK: id, UK: strategy_code) - 매매 전략 설정
- `strategy_execution_log` (PK: id) - 전략 실행 로그

### 8. 운영 로그
- `scheduler_execution_log` (PK: id) - 스케줄러 실행 로그
- `broker_api_log` (PK: id) - 증권사 API 호출 로그
- `external_api_call_log` (PK: id) - 외부 API 호출 로그
- `system_error_log` (PK: id) - 시스템 에러 로그

## 주요 Enum/상수 값

| 테이블 | 컬럼 | 허용값 |
|--------|------|--------|
| stock_master | market_type | KOSPI, KOSDAQ |
| ai_decision | decision | BUY, SELL, HOLD |
| ai_decision | risk_level | LOW, MEDIUM, HIGH |
| ai_decision | decision_status | CREATED, RISK_PASSED, RISK_FAILED, ORDERED, EXPIRED |
| order_request | order_side | BUY, SELL |
| order_request | order_type | MARKET, LIMIT |
| order_request | order_status | READY, ORDERED, PARTIALLY_FILLED, FILLED, FAILED, CANCELLED |
| ai_feedback | evaluation_type | DAILY, WEEKLY, MONTHLY, HOLDING_END |
| news_sentiment | sentiment | POSITIVE, NEUTRAL, NEGATIVE |
| ai_decision_factor | factor_type | TECHNICAL, NEWS, DART, FUNDAMENTAL, SUPPLY_DEMAND |
| ai_decision_factor | factor_direction | POSITIVE, NEGATIVE, NEUTRAL |
| external_api_call_log | provider | KIS, DART, NAVER, OPENAI |

## MyBatis Domain 클래스 매핑 규칙

- DB snake_case → Java camelCase (mybatis map-underscore-to-camel-case=true)
- DECIMAL → BigDecimal
- BIGINT id → Long
- INT → Integer
- TINYINT(1) → Boolean
- VARCHAR → String
- DATE → LocalDate
- DATETIME → LocalDateTime
- JSON → String (필요시 TypeHandler 추가)

