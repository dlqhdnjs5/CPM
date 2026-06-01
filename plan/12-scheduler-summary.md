# 스케줄러 구현 완료 현황

모든 스케줄러가 에러 없이 구현되어 있음. `@EnableScheduling` 활성화 확인됨.

---

## 구현된 스케줄러 (10개)

| 스케줄러 | 실행 시각 | 하는 일 |
|---------|---------|--------|
| `AccountSyncScheduler` | 08:30, 15:35 (평일) | KIS 잔고 조회 → account_balance + portfolio_position 저장 |
| `DailyPriceSyncScheduler` | 08:35 (평일) | 활성 종목 일봉 수집 → stock_price_daily 저장 |
| `NewsCollectScheduler` | 08:40, 16:10 (평일) | 활성 종목 뉴스 수집 → stock_news 저장 |
| `DartCollectScheduler` | 08:45 (평일) | 공시 수집 + 주요 이벤트 분류/요약 |
| `AiDecisionScheduler` | 장중 매 30분 (09:30~15:00) | 활성 종목 AI 판단 생성 → ai_decision 저장 |
| `OrderExecutionScheduler` | 장중 매 30분 (AI 5분 후) | CREATED 상태 BUY → 리스크 검증 → 주문 실행 |
| `ExecutionSyncScheduler` | 장중 매 10분 + 15:35 | KIS 체결 조회 → order_execution 저장 → 포트폴리오 갱신 |
| `DailyFeedbackScheduler` | 16:00 (평일) | 오늘 AI 판단 성과 평가 → ai_feedback 저장 |
| `DailyProfitLossScheduler` | 16:05 (평일) | 일간 수익률 계산 → portfolio_profit_loss 저장 |
| `SchedulerLogSupport` | (유틸) | 중복 실행 방지 + 로그 기록 + 장중/평일 체크 |

---

## 안전장치

1. **중복 실행 방지**: `logSupport.isAlreadyRunning(NAME)` — scheduler_execution_log에 RUNNING 상태 체크
2. **평일 체크**: `logSupport.isWeekday()` — 주말 스킵
3. **장중 체크**: `logSupport.isMarketOpen()` — 09:00~15:30 외 스킵
4. **HOLD_BY_REVIEW 차단**: 재검토에서 HOLD 나온 건 주문 안 함
5. **리스크 검증 필수**: 주문 전 반드시 `riskService.checkAndSave()` 통과해야 함
6. **KIS API 장외 500 에러 대응**: 장 마감 후 체결 조회 시 500 에러 발생 가능 → catch에서 graceful 처리

---

## 서버 시작하면 자동 실행됨

`application.yml`에 별도 설정 없이 `@EnableScheduling` + `@Scheduled(cron=...)` 으로 동작.
평일 장 시간에 서버가 켜져 있으면 자동으로 돌아감.

---

## 알려진 이슈

- KIS 체결 조회 API: 장 마감 후(~16:00 이후) 호출 시 500 에러 발생 가능
- Access Token: 요청 시 자동 갱신 (만료 10분 전 재발급), 별도 스케줄러 불필요
- KIS API 초당 호출 제한: 종목 많아지면 sleep 추가 필요

---

## 향후 추가 (2~3단계)

- `WeeklyFeedbackScheduler` — 매주 토요일 09:00
- `MonthlyFeedbackScheduler` — 매월 1일 09:00
- `RealtimeQuoteScheduler` — 장중 매 5분 현재가 저장
- `PortfolioSnapshotScheduler` — 15:40 스냅샷

