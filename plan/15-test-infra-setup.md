# Plan 15: 테스트 인프라 세팅

## Understanding

Plan 14에서 정의한 14개 테스트(단위 10 + Mapper 2 + 스케줄러 1 + 통합 1) + 수동 트리거 3종을 작성하기 전, 테스트 실행 기반을 먼저 정비한다.

현재 상태:

- `src/test/java/com/bowon/cpm/` 디렉토리는 비어 있다.
- `build.gradle`에는 `spring-boot-starter-test`만 있고 MyBatis 테스트/Testcontainers/Awaitility 등 보조 의존성은 없다.
- `application.yml` / `application-local.yml`만 존재하며 `application-test.yml`은 없다.
- 운영 DB는 로컬 MySQL 8 `cpm` 스키마 사용 중.

이 플랜은 **테스트 실행 기반 + 공용 Fixture만** 만든다. 실제 테스트 케이스 코드는 Plan 16~18에서 작성한다.

---

## Implementation Plan

### 1. 테스트 DB 전략 — **운영 `cpm` DB + `@Transactional` 자동 롤백**

사용자 결정 사항: **별도 스키마 만들지 않음**. 운영 `cpm` DB를 그대로 쓰되 Spring Test의 `@Transactional` 자동 롤백으로 격리.

#### 동작 원리

- `@SpringBootTest` / `@MybatisTest` 클래스(또는 메서드)에 `@Transactional`을 붙이면 테스트 메서드 종료 시 자동 롤백 (Spring TestContext가 처리).
- `@Sql(scripts="...")`로 seed 주입 가능. 단, `@Sql`은 기본적으로 테스트 트랜잭션 **밖에서** 실행되므로 옵션 조정 필요:
  ```java
  @Sql(scripts = "/sql/seed-feedback.sql",
       config = @SqlConfig(transactionMode = TransactionMode.ISOLATED))
  ```
  또는 테스트 트랜잭션 내부에서 실행하려면 `INFERRED`.

#### ✅ 안전한 경우

- Mapper 테스트 (`@MybatisTest`) — 단일 트랜잭션 → 롤백 OK
- Service 단위/통합 — 메서드 전체가 하나의 트랜잭션이면 OK

#### ⚠️ 롤백 안 되는 경우 (주의)

1. **`@Transactional(propagation = REQUIRES_NEW)`** — 별도 트랜잭션이라 테스트 롤백과 무관, **커밋됨**
2. **별도 스레드 / `@Async` / `@Scheduled` 내부 트랜잭션** — 테스트 트랜잭션 컨텍스트 미상속
3. **DDL** (TRUNCATE/ALTER) — 암묵적 커밋
4. **MyBatis flush 없이 read 검증** — `@Transactional` + same connection이라 read는 정상이지만, 별도 트랜잭션에서 검증하려면 flush 필요

#### 대응

- 위 ⚠️ 케이스에 해당하는 테스트는 `@Sql(executionPhase = AFTER_TEST_METHOD)`로 cleanup SQL 명시적 실행:
  ```java
  @Sql(scripts = "/sql/cleanup-feedback.sql",
       executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
       config = @SqlConfig(transactionMode = TransactionMode.ISOLATED))
  ```
- 또는 테스트 데이터에 식별 가능한 marker(예: `stock_code='TEST_999'`)를 넣고 cleanup에서 marker 기준 DELETE.

#### 권장 패턴

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional   // ← 자동 롤백
class SomeServiceIntegrationTest {
    // 테스트 끝나면 모든 INSERT/UPDATE 자동 롤백
}
```

```java
@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @MybatisTest는 기본적으로 @Transactional 포함 → 자동 롤백
class SomeMapperTest { }
```

### 2. `application-test.yml` 신규 작성

```yaml
spring:
  config:
    activate:
      on-profile: test
  datasource:
    url: jdbc:mysql://localhost:3306/cpm?serverTimezone=Asia/Seoul&characterEncoding=utf8mb4
    username: givememoney
    password: givememoney1234
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true

doncopy:
  trading:
    mode: PAPER
    enabled: false

external:
  kis: { app-key: test, app-secret: test, account-no: 0000-00, account-product-code: '01',
         base-url: http://localhost, mock-base-url: http://localhost,
         websocket-url: ws://localhost, token-path: /oauth2/tokenP, approval-key-path: /test }
  dart: { base-url: http://localhost, api-key: test }
  naver: { base-url: http://localhost, client-id: test, client-secret: test }
  openai: { base-url: http://localhost, api-key: test, model: gpt-4o-mini }
```

운영과 동일 `cpm` DB 사용 (별도 스키마 X). 외부 API Key는 모두 dummy → 실 호출은 Mock/stub으로 차단.

> ⚠️ 운영 DB를 직접 쓰므로 **롤백 실패 시 데이터 오염 위험**. 모든 테스트 클래스에 `@Transactional` 강제 + cleanup SQL marker 사용 규칙 준수.

### 3. (삭제) ~~`cpm_test` 스키마 DDL 적용~~

별도 스키마 없음.

### 4. `build.gradle` 테스트 의존성 보강

```groovy
dependencies {
    // ...existing...
    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.4'
    // (옵션 B 채택 시) testImplementation 'org.testcontainers:mysql:1.20.4'
    // (옵션 B 채택 시) testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
}
```

- `assertj-core`, `mockito-core`, `mockito-junit-jupiter`는 `spring-boot-starter-test`에 포함되어 별도 추가 불필요.

### 5. 공용 Test Fixture 패키지 신설

`src/test/java/com/bowon/cpm/support/`

- `TestProfiles.java` — 상수 `public static final String TEST = "test";`
- `fixture/AiDecisionFixture.java` — `aBuyDecision()`, `aSellDecision()`, `withHoldingDays(int)` 등 빌더
- `fixture/StockPriceDailyFixture.java` — 30일 OHLCV seed 생성 헬퍼
- `fixture/AiFeedbackFixture.java`
- `support/SqlSeedRunner.java` — `@Sql` 대신 JDBC로 seed.sql 실행 (필요 시)

### 6. `src/test/resources/` 정비

- `application-test.yml` (위 2번)
- **`cleanup-feedback.sql` 작성 불필요** — Plan 17/18에서 스케줄러 통합 테스트와 풀 플로우 통합 테스트(옵션 B-1)를 제거했으므로 운영 DB write가 없는 형태로 통일됨. 추후 옵션 B-2(축소 통합 테스트) 채택 시에만 marker 기반 DELETE SQL 작성.

### 7. 베이스 추상 클래스 (선택)

- `AbstractMybatisTest` — `@MybatisTest @ActiveProfiles("test") @Import(...)` 공통화
- `AbstractIntegrationTest` — `@SpringBootTest @ActiveProfiles("test")` 공통화 + `@BeforeEach` cleanup

---

## Files / Changes

### 신규
- `src/test/resources/application-test.yml`
- `src/test/resources/sql/cleanup.sql`
- `src/test/java/com/bowon/cpm/support/TestProfiles.java`
- `src/test/java/com/bowon/cpm/support/AbstractMybatisTest.java`
- `src/test/java/com/bowon/cpm/support/AbstractIntegrationTest.java`
- `src/test/java/com/bowon/cpm/support/fixture/AiDecisionFixture.java`
- `src/test/java/com/bowon/cpm/support/fixture/StockPriceDailyFixture.java`
- `src/test/java/com/bowon/cpm/support/fixture/AiFeedbackFixture.java`

### 수정
- `build.gradle` — `mybatis-spring-boot-starter-test` 추가 (옵션 B 채택 시 Testcontainers 추가)

### DB
- `cpm_test` 스키마 신규 생성 (사용자 수동) + 운영 DDL 동일 적용
- **DDL 변경 없음**

---

## Test Steps

- `./gradlew test` 실행 시 빈 테스트라도 빌드 통과 확인
- `AbstractMybatisTest`를 상속한 sanity 테스트(`select 1`) 1건만 작성해서 DB 연결 확인 (다음 플랜으로 넘기기 직전 검증)

---

## Risks / Assumptions

- **위험 (大)**: 운영 `cpm` DB를 직접 쓰므로 롤백 실패 시 운영 데이터 오염 가능 →
  - 모든 테스트 클래스에 `@Transactional` 강제 (코드 리뷰로 체크)
  - 테스트 데이터는 식별 가능한 marker(`stock_code='TEST_999'`, `corp_code='TEST_*'` 등) 사용
  - `REQUIRES_NEW` / 별도 스레드 테스트는 `@Sql(AFTER_TEST_METHOD)` cleanup 필수
  - CI 환경에서 운영 DB 접속 차단 (또는 환경변수로 DB 분리)
- **위험**: `@Sql` 기본 동작은 테스트 트랜잭션 밖이라 seed가 커밋됨 → `transactionMode = ISOLATED` 또는 `INFERRED` 명시
- **위험**: 동시 실행 시 marker 충돌 → 테스트마다 UUID 기반 marker (`TEST_${uuid}`)
- **위험**: 테스트 실패로 롤백 안 된 데이터 누적 → 주기적으로 `cleanup-feedback.sql` 수동 실행

---

## 진행 순서 제안

1. ~~사용자 승인 (테스트 DB 전략)~~ → 결정됨: 운영 cpm DB + `@Transactional` 롤백
2. `build.gradle` 의존성 추가 → `./gradlew build` 통과 확인
3. `application-test.yml` + `cleanup-feedback.sql` + Fixture/Abstract 클래스 작성
4. sanity 테스트 1건(`select 1`)으로 DB 연결 + 롤백 동작 검증
5. → Plan 16 진입







