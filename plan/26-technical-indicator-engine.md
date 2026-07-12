# Plan 26: 기술지표 계산 엔진

## Understanding

AI 프롬프트는 이미 `stock_indicator_daily` 최신 1건을 읽도록 구성되어 있다.
하지만 현재는 일봉 가격에서 기술지표를 계산해 `stock_indicator_daily`에 저장하는 서비스와 스케줄러가 부족하다.

## Implementation Plan

- 일봉 기반 기술지표 계산기를 추가한다.
- 계산 대상은 활성 종목 전체이며, 종목별 최근 180일 일봉을 기준으로 최신 거래일 지표를 계산한다.
- 계산 지표:
  - MA5, MA20, MA60, MA120
  - RSI14
  - MACD, MACD Signal, MACD Histogram
  - Bollinger Upper/Middle/Lower
  - 20일 수익률 변동성
  - 거래량 변화율
- 계산 결과는 `stock_indicator_daily`에 upsert한다.
- 장 시작 전/마감 후 스케줄러와 수동 Admin API를 추가한다.

## Files / Changes

- `market/service/TechnicalIndicatorCalculator.java`
- `market/service/TechnicalIndicatorService.java`
- `market/mapper/StockIndicatorDailyMapper.java/.xml`
- `market/mapper/StockPriceDailyMapper.java/.xml`
- `scheduler/IndicatorCalculateScheduler.java`
- `admin/MarketController.java`
- 테스트: 계산기/서비스 단위 테스트

## Test Steps

- 충분한 일봉으로 MA/RSI/MACD/Bollinger 계산 검증
- 일봉 부족 시 계산 가능한 값만 저장하고 오류 없이 스킵하는지 검증
- 전체 테스트 `./gradlew.bat test`

## Risks / Assumptions

- RSI는 단순 14일 평균 gain/loss 기준으로 계산한다.
- MACD는 EMA12/EMA26, signal EMA9 기준이다.
- 분봉 지표는 이후 단계에서 별도 구현한다.
