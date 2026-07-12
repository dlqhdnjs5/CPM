## Understanding

거래량과 거래대금이 얇은 종목은 자동매매 후보로 위험하다. 현재 watchlist 점수에는 유동성 점수가 있지만, 기준 미달 종목을 강하게 배제하지는 않는다. 주문 직전 리스크 검증에도 거래대금 기반 차단이 없다.

## Implementation Plan

1. 최근 20거래일 평균 거래량/거래대금을 candidate metrics에 추가한다.
2. watchlist 후보 선정에서 평균 거래량 100,000주 미만 또는 평균 거래대금 30억원 미만 종목을 제외한다.
3. scoring reason에 평균 거래량/거래대금을 남겨 판단 근거를 확인할 수 있게 한다.
4. 리스크 검증에서 BUY 주문 전 최근 20거래일 평균 거래량/거래대금을 다시 확인해 기준 미달이면 차단한다.
5. 관련 단위테스트를 추가/수정한다.

## Files / Changes

- `WatchlistProperties`: 유동성 기준 설정 추가
- `StockCandidateMetrics`: 20일 평균 거래량/거래대금 필드 추가
- `StockCandidateScoreMapper.xml`: 최근 20거래일 평균 집계 및 candidate 필터 추가
- `WatchlistScoringEngine`: 평균 거래대금 기반 유동성 점수/근거 보강
- `RiskService`: BUY 리스크 검증 전 유동성 기준 확인
- `StockPriceDailyMapper`: 최근 20거래일 평균 거래량/거래대금 조회
- 테스트 코드: watchlist scoring, risk service 중심

## Test Steps

- 유동성 점수 reason 테스트
- 리스크 검증에서 유동성 부족 시 실패하는 테스트
- 관련 Gradle focused test 실행

## Risks / Assumptions

- DB 스키마 변경은 없다.
- 평균 거래대금 기준 기본값은 30억원, 평균 거래량 기준 기본값은 100,000주로 둔다.
- 보유 종목은 watchlist 유지 규칙이 별도로 있으므로, 신규 후보 선정 차단과 주문 차단에 집중한다.
