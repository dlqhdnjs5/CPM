# 스케줄러 설계 및 실행 계획

## 스케줄러란?

서버가 혼자서 정해진 시간마다 자동으로 일을 처리하는 것.
사람이 직접 API를 누르지 않아도 알아서 데이터를 수집하고, AI 판단을 내리고, 주문을 실행한다.

---

## 한국 주식시장 시간표

```
08:30 ~ 09:00   장 시작 준비
09:00 ~ 15:30   장중 (주문 가능)
15:30 ~ 16:00   장 마감 후 처리
```

---

## 전체 스케줄러 목록

### 🌅 장 시작 전 (08:30 ~ 09:00)

| 스케줄러 | 실행 시각 | 하는 일 | 이유 |
|---------|---------|--------|------|
| `AccountSyncScheduler` | 08:30 | KIS 잔고 조회 → DB 저장 | 오늘 얼마로 시작하는지 파악 |
| `DailyPriceSyncScheduler` | 08:35 | 종목 일봉 수집 → DB 저장 | AI가 최신 가격 흐름으로 판단하도록 |
| `NewsCollectScheduler` | 08:40 | 네이버 뉴스 수집 → DB 저장 | 오늘 아침 뉴스 반영 |
| `DartCollectScheduler` | 08:45 | DART 공시 수집 → DB 저장 | 전날 밤 공시 반영 |

---

### 📈 장중 (09:00 ~ 15:30)

| 스케줄러 | 실행 주기 | 하는 일 | 이유 |
|---------|---------|--------|------|
| `AiDecisionScheduler` | 매 30분 | AI 판단 생성 → DB 저장 | 시장 상황이 바뀌면 판단도 바뀌어야 함 |
| `RiskCheckScheduler` | AI 판단 직후 | 리스크 검증 | AI가 BUY해도 바로 주문하면 안 됨 |
| `OrderExecutionScheduler` | 매 30분 (AI 판단 후 5분) | 리스크 통과한 건 주문 실행 | |
| `ExecutionSyncScheduler` | 매 10분 | KIS 체결 내역 동기화 | 주문이 실제로 됐는지 확인 |
| `RealtimeQuoteScheduler` | 매 5분 | 현재가 저장 | 포트폴리오 평가금액 실시간 반영 |

---

### 🌆 장 마감 후 (15:30 ~ 16:30)

| 스케줄러 | 실행 시각 | 하는 일 | 이유 |
|---------|---------|--------|------|
| `ExecutionSyncScheduler` (최종) | 15:35 | 오늘 체결 내역 최종 동기화 | 장 마감 후 미체결 정리 |
| `PortfolioSnapshotScheduler` | 15:40 | 포트폴리오 스냅샷 저장 | 오늘 하루 결과 기록 |
| `DailyFeedbackScheduler` | 16:00 | AI 판단 결과 평가 (성공/실패) | AI가 오늘 맞게 판단했는지 피드백 |
| `DailyProfitLossScheduler` | 16:05 | 일간 수익률 계산 → DB 저장 | 오늘 얼마 벌었는지 기록 |
| `NewsCollectScheduler` | 16:10 | 장 마감 후 뉴스 추가 수집 | 오후 뉴스 반영 |

---

### 📅 주간 / 월간

| 스케줄러 | 실행 시각 | 하는 일 |
|---------|---------|--------|
| `WeeklyFeedbackScheduler` | 매주 토요일 09:00 | 이번 주 AI 판단 성과 평가 |
| `MonthlyFeedbackScheduler` | 매월 1일 09:00 | 이번 달 AI 판단 성과 평가 |
| `DartCollectScheduler` (주간) | 매주 토요일 10:00 | DART corp_code 전체 재동기화 |

---

## 초기 MVP 구현 우선순위

처음부터 모든 스케줄러를 만들면 복잡하다.  
아래 순서로 단계적으로 추가한다.

### 1단계 (지금 바로 구현)

```
AccountSyncScheduler      → 08:30, 장 마감 후 15:35
DailyPriceSyncScheduler   → 08:35
AiDecisionScheduler       → 장중 매 30분
ExecutionSyncScheduler    → 장중 매 10분
DailyFeedbackScheduler    → 16:00
DailyProfitLossScheduler  → 16:05
```

### 2단계 (안정화 후 추가)

```
NewsCollectScheduler
DartCollectScheduler
RealtimeQuoteScheduler
PortfolioSnapshotScheduler
```

### 3단계 (운영 안정화 후)

```
WeeklyFeedbackScheduler
MonthlyFeedbackScheduler
RiskCheckScheduler (별도 분리)
OrderExecutionScheduler (별도 분리)
```

---

## 중복 실행 방지

스케줄러가 두 번 돌면 주문이 두 번 나갈 수 있다.  
반드시 막아야 한다.

방법:
- `scheduler_execution_log` 테이블에 실행 기록
- 이미 실행 중이면 스킵
- 실패 시 FAILED 기록

```
scheduler_name + started_at 기준으로
"지금 실행 중인 건 없는지" 먼저 확인 → 없으면 실행 → 끝나면 기록
```

---

## 장외 시간 주문 방지

스케줄러가 실수로 장외에 주문을 내면 KIS에서 오류가 난다.  
주문 스케줄러는 반드시 아래 조건을 확인한다.

```java
// 평일 09:00 ~ 15:30 사이에만 주문
LocalTime now = LocalTime.now();
DayOfWeek day = LocalDate.now().getDayOfWeek();

if (day == SATURDAY || day == SUNDAY) return; // 주말 스킵
if (now.isBefore(LocalTime.of(9, 0))) return; // 장 전 스킵
if (now.isAfter(LocalTime.of(15, 30))) return; // 장 후 스킵
```

