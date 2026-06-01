# Plan: 1단계 - 프로젝트 기본 구조

## Understanding

현재 상태:
- Spring Boot 3.5.x + Java 21 프로젝트 생성됨
- build.gradle: MyBatis, WebFlux(WebClient), Spring AI, MySQL, Lombok 의존성 포함
- application.properties: DataSource, MyBatis 기본 설정 존재

목표: 공통 구조(설정/예외/응답/유틸)를 완성하여 이후 도메인 개발의 기반을 만든다.

---

## Implementation Plan

### 1. application.properties → application.yml 전환
- 기존 설정 유지하며 yml 형식으로 전환
- 외부 API 설정(KIS, DART, Naver, OpenAI) placeholder 추가
- 로깅 설정 추가
- 트레이딩 모드(REAL) 설정 추가 — 실전투자 기준, 모의투자 미사용

### 2. common/config
- `MyBatisConfig` - MapperScan, TypeHandler 설정
- `WebClientConfig` - KIS / DART / Naver / OpenAI 용 WebClient Bean 분리
- `PropertiesConfig` - ConfigurationProperties 등록

### 3. common/exception
- `CpmException` - 비즈니스 예외 베이스
- `ExternalApiException` - 외부 API 호출 예외
- `GlobalExceptionHandler` - `@RestControllerAdvice`

### 4. common/response
- `ApiResponse<T>` - 공통 REST API 응답 래퍼

### 5. common/util
- `MaskingUtils` - API Key, 계좌번호 마스킹

### 6. broker/kis Properties
- `KisProperties` - KIS 설정 record
- `DartProperties` - DART 설정 record
- `NaverProperties` - Naver 설정 record
- `OpenAiProperties` - OpenAI 설정 record

---

## Files / Changes

| 파일 | 작업 |
|------|------|
| `src/main/resources/application.properties` | 삭제 후 `application.yml` 생성 |
| `src/main/resources/application-local.yml` | 로컬 개발용 (Git 제외) |
| `common/config/MyBatisConfig.java` | 신규 |
| `common/config/WebClientConfig.java` | 신규 |
| `common/config/PropertiesConfig.java` | 신규 |
| `common/exception/CpmException.java` | 신규 |
| `common/exception/ExternalApiException.java` | 신규 |
| `common/exception/GlobalExceptionHandler.java` | 신규 |
| `common/response/ApiResponse.java` | 신규 |
| `common/util/MaskingUtils.java` | 신규 |
| `broker/kis/KisProperties.java` | 신규 |
| `dart/client/DartProperties.java` | 신규 |
| `news/client/NaverProperties.java` | 신규 |
| `ai/client/OpenAiProperties.java` | 신규 |
| `.gitignore` | application-local.yml 추가 |

---

## Test Steps

1. `./gradlew bootRun` 실행 → 정상 기동 확인
2. MySQL 연결 확인 (`spring.datasource` 로그)
3. `GET /api/health` 등 기본 엔드포인트 없어도 기동 에러 없으면 OK
4. WebClient Bean 주입 에러 없는지 확인

---

## Risks / Assumptions

- DB: username=`givememoney`, password=`givememoney123` 확정
- **실전투자 기준** — 모의투자 API 미사용. KIS 실전 base-url 사용 (`https://openapi.koreainvestment.com:9443`)
- 주문 실행은 7단계(가상매매 PAPER 모드) 없이 바로 실전 API 호출 흐름으로 구현
- Spring Boot 3.5.15-SNAPSHOT 버전 사용 중 → 안정 버전이 아니므로 의존성 해결 실패 가능성 있음 (사용자 판단)
- `application-local.yml`은 `.gitignore`에 추가하되 실제 키 값은 사용자가 직접 입력



