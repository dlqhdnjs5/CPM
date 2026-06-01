---
applyTo: '**'
description: 'Backend 개발 컨벤션 및 프로젝트 구조 지시문'
---

# 백엔드 개발 지시문

## 1. 기술 스택 및 ORM

- Java 21, Spring Boot 3.5.x, MyBatis, MySQL 8
- **JPA 사용 금지**. MyBatis를 기본 SQL 접근 방식으로 사용한다.
- WebClient (Spring WebFlux의 non-blocking HTTP client)를 외부 API 호출에 사용한다.
- Lombok 적극 활용 (@Getter, @Builder, @RequiredArgsConstructor 등)

## 2. 패키지 구조

```text
com.bowon.cpm
├── CpmApplication.java
├── common
│   ├── config/        # DataSource, MyBatis, WebClient 설정
│   ├── exception/     # 공통 예외, GlobalExceptionHandler
│   ├── response/      # 공통 API 응답 래퍼
│   └── util/          # 유틸 클래스 (MaskingUtils 등)
├── stock/             # 종목 기본 정보
├── market/            # 시세, 기술적 지표
├── dart/              # OpenDART 공시/재무
├── news/              # 네이버 뉴스
├── ai/                # AI 판단
├── risk/              # 리스크 검증
├── order/             # 주문/체결
├── broker/            # 증권사 API 추상화
├── portfolio/         # 계좌/포트폴리오
├── feedback/          # AI 피드백
├── scheduler/         # 스케줄러
└── admin/             # 관리 API
```

각 도메인 하위 구조:
```text
{domain}/
├── domain/      # VO, DTO, Domain 객체
├── mapper/      # MyBatis Mapper 인터페이스
├── service/     # 비즈니스 로직
└── controller/  # REST Controller (필요 시)
```

## 3. MyBatis 규칙

- Mapper XML은 `src/main/resources/mapper/{domain}/` 하위에 둔다.
- Mapper 인터페이스에는 `@Mapper` 어노테이션을 사용한다.
- INSERT/UPDATE 시 `useGeneratedKeys="true" keyProperty="id"` 활용.
- 기간 조회 SQL은 반드시 인덱스 컬럼을 WHERE 조건에 포함한다.
- 대량 INSERT는 `<foreach>` 배치 활용.
- SQL에 `SELECT *` 사용 금지, 명시적 컬럼 나열.

## 4. 네이밍 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| Domain 클래스 | 테이블명 PascalCase | StockMaster, AiDecision |
| Mapper 인터페이스 | {Domain}Mapper | StockMasterMapper |
| Service | {도메인}Service | StockService, OrderService |
| Controller | {도메인}Controller | StockController |
| DTO | {용도}{Domain}Dto | CreateOrderRequestDto |
| 메서드 (조회) | find, get, select | findByStockCode |
| 메서드 (생성) | insert, create | insertStockNews |
| 메서드 (수정) | update | updateOrderStatus |
| 메서드 (삭제) | delete | deleteByExpiredDate |

## 5. API 응답 규칙

모든 REST API는 공통 응답 래퍼를 사용한다:

```java
public record ApiResponse<T>(
    boolean success,
    String message,
    T data
) {
    public static <T> ApiResponse<T> ok(T data) { ... }
    public static <T> ApiResponse<T> error(String message) { ... }
}
```

## 6. 예외 처리

- 비즈니스 예외: 커스텀 Exception 클래스 생성 → `@RestControllerAdvice`에서 처리
- 외부 API 실패: 로그 저장 후 graceful 처리, 시스템 중단 금지
- 주문 관련 예외: 반드시 `order_status_history`에 기록

## 7. 날짜/시간

- `LocalDate`, `LocalDateTime` 사용 (java.util.Date 금지)
- DB 타임존: Asia/Seoul
- MyBatis에서 날짜 파라미터는 `java.time` 타입 그대로 사용

## 8. 트랜잭션

- Service 레이어에 `@Transactional` 적용
- 읽기 전용은 `@Transactional(readOnly = true)`
- 외부 API 호출은 트랜잭션 밖에서 수행 (트랜잭션 내 네트워크 호출 금지)

## 9. 로깅

- SLF4J + Logback 사용
- 클래스에 `@Slf4j` (Lombok)
- 외부 API 호출은 반드시 로그 테이블에도 저장 (broker_api_log, external_api_call_log)
- 민감 정보 마스킹 필수

## 10. 테스트

- 단위 테스트: JUnit 5 + Mockito
- 통합 테스트: `@SpringBootTest` + 실제 DB (테스트용 프로파일)
- 외부 API는 Mock 처리

## 11. 플랜 작성 규칙

모든 비단순 작업 전 `plan/` 디렉토리에 플랜을 작성하고 승인받는다.

```md
## Understanding
(요구사항 이해)

## Implementation Plan
(구현 계획)

## Files / Changes
(변경 대상 파일 목록)

## Test Steps
(테스트 방법)

## Risks / Assumptions
(리스크 및 가정)
```

## 12. 금지사항

- JPA/Hibernate 사용 금지 (MyBatis만 사용)
- `SELECT *` 금지
- 외부 API Key 하드코딩 금지
- DB 스키마 임의 변경 금지
- 요구사항 외 리팩토링 금지
- 새 라이브러리 임의 도입 금지 (먼저 제안)
- 패키지명/클래스명/테이블명 임의 변경 금지
