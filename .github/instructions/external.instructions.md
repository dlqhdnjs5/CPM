---
applyTo: '**'
description: '외부 API 연동 구현 지시문 (KIS, DART, Naver, OpenAI)'
---
Provide project context and coding guidelines that AI should follow when generating code, answering questions, or reviewing changes.

# 외부 API 연동 구현 지시문

## 1. 목적

이 문서는 돈복사 프로젝트에서 사용하는 외부 API 연동 방식을 Copilot Agent가 이해하고 구현하기 위한 지시문이다.

초기 MVP에서 사용하는 외부 API는 다음 4개다.

```text
1. 한국투자증권 Open API
2. OpenDART API
3. 네이버 뉴스 검색 API
4. OpenAI API
```

모든 외부 API 호출은 다음 원칙을 따른다.

```text
API Key, Secret, 계좌번호는 절대 하드코딩하지 않는다.
정확한 값이 없으면 "찾아서 넣으세요" placeholder를 사용한다.
실제 API endpoint, TR ID, request field가 확실하지 않으면 공식 문서를 기준으로 확인하고 구현한다.
모호한 값은 임의로 추측하지 말고 "찾아서 넣으세요" 또는 TODO 주석으로 남긴다.
외부 API 호출 결과는 성공/실패 모두 로그 테이블에 저장한다.
API Key, Secret, 계좌번호 전체값은 로그에 저장하지 않는다.
DB 스키마 변경이 필요하면 반드시 사용자에게 먼저 보고하고 승인받는다.
```

---

## 2. application.yml 설정 예시

실제 값은 넣지 않는다.

```yaml
doncopy:
  trading:
    mode: PAPER
    enabled: false

external:
  kis:
    base-url: ${KIS_BASE_URL:https://openapi.koreainvestment.com:9443}
    mock-base-url: ${KIS_MOCK_BASE_URL:찾아서 넣으세요}
    websocket-url: ${KIS_WEBSOCKET_URL:찾아서 넣으세요}
    app-key: ${KIS_APP_KEY:찾아서 넣으세요}
    app-secret: ${KIS_APP_SECRET:찾아서 넣으세요}
    account-no: ${KIS_ACCOUNT_NO:찾아서 넣으세요}
    account-product-code: ${KIS_ACCOUNT_PRODUCT_CODE:01}
    token-path: ${KIS_TOKEN_PATH:/oauth2/tokenP}
    approval-key-path: ${KIS_APPROVAL_KEY_PATH:찾아서 넣으세요}

  dart:
    base-url: ${DART_BASE_URL:https://opendart.fss.or.kr}
    api-key: ${DART_API_KEY:찾아서 넣으세요}

  naver:
    base-url: ${NAVER_BASE_URL:https://openapi.naver.com}
    client-id: ${NAVER_CLIENT_ID:찾아서 넣으세요}
    client-secret: ${NAVER_CLIENT_SECRET:찾아서 넣으세요}

  openai:
    base-url: ${OPENAI_BASE_URL:https://api.openai.com}
    api-key: ${OPENAI_API_KEY:찾아서 넣으세요}
    model: ${OPENAI_MODEL:gpt-5.5}
```

주의:

```text
application.yml에는 placeholder만 둔다.
실제 값은 환경변수 또는 application-local.yml에서 관리한다.
application-local.yml은 Git에 커밋하지 않는다.
```

---

## 3. 공통 WebClient 설정

외부 API별로 WebClient Bean을 분리한다.

```java
package com.doncopy.common.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient kisWebClient(
            WebClient.Builder builder,
            @Value("${external.kis.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(connector())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient dartWebClient(
            WebClient.Builder builder,
            @Value("${external.dart.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(connector())
                .build();
    }

    @Bean
    public WebClient naverWebClient(
            WebClient.Builder builder,
            @Value("${external.naver.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(connector())
                .build();
    }

    @Bean
    public WebClient openAiWebClient(
            WebClient.Builder builder,
            @Value("${external.openai.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(connector())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private ReactorClientHttpConnector connector() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(15));

        return new ReactorClientHttpConnector(httpClient);
    }
}
```

---

## 4. 공통 외부 API 로그 규칙

모든 외부 API 호출은 아래 테이블 중 하나에 로그를 저장한다.

```text
한국투자증권 API → broker_api_log
OpenDART API → external_api_call_log
네이버 뉴스 API → external_api_call_log
OpenAI API → external_api_call_log
```

로그 저장 시 민감 정보는 마스킹한다.

```java
package com.doncopy.common.util;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    public static String maskAccountNo(String accountNo) {
        if (accountNo == null || accountNo.length() < 4) {
            return "****";
        }
        return accountNo.substring(0, 4) + "****";
    }

    public static String maskSecret(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
```

---

# 5. 한국투자증권 Open API 연동

## 5.1 구현 원칙

한국투자증권 Open API는 이 프로젝트의 증권사 연동 핵심이다.

구현 대상:

```text
Access Token 발급/갱신
계좌 잔고 조회
보유 종목 조회
현재가 조회
일봉 조회
매수 주문
매도 주문
주문 체결 조회
```

주의:

```text
한국투자증권 API는 API별 TR ID가 중요하다.
TR ID는 공식 문서에서 확인해서 정확히 넣는다.
확실하지 않은 TR ID는 "찾아서 넣으세요"로 둔다.
실전/모의투자 URL과 TR ID가 다를 수 있으므로 설정으로 분리한다.
주문 API는 절대 중복 호출하지 않도록 idempotency_key를 사용한다.
주문 API 타임아웃 발생 시 즉시 재주문하지 말고 주문/체결 조회를 먼저 수행한다.
```

---

## 5.2 KIS 설정 Properties

```java
package com.doncopy.broker.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.kis")
public record KisProperties(
        String baseUrl,
        String mockBaseUrl,
        String websocketUrl,
        String appKey,
        String appSecret,
        String accountNo,
        String accountProductCode,
        String tokenPath,
        String approvalKeyPath
) {
}
```

ConfigurationProperties 등록:

```java
package com.doncopy.common.config;

import com.doncopy.broker.kis.KisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KisProperties.class)
public class PropertiesConfig {
}
```

---

## 5.3 Access Token 발급 예시

요청 구조 예시:

```http
POST /oauth2/tokenP
Content-Type: application/json
```

```json
{
  "grant_type": "client_credentials",
  "appkey": "찾아서 넣으세요",
  "appsecret": "찾아서 넣으세요"
}
```

Java 예시:

```java
package com.doncopy.broker.kis;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KisAuthClient {

    private final WebClient kisWebClient;
    private final KisProperties properties;

    private volatile String accessToken;
    private volatile LocalDateTime expiresAt;

    public String getAccessToken() {
        if (accessToken == null || expiresAt == null || LocalDateTime.now().isAfter(expiresAt.minusMinutes(10))) {
            issueAccessToken();
        }
        return accessToken;
    }

    private synchronized void issueAccessToken() {
        if (accessToken != null && expiresAt != null && LocalDateTime.now().isBefore(expiresAt.minusMinutes(10))) {
            return;
        }

        KisTokenResponse response = kisWebClient.post()
                .uri(properties.tokenPath())
                .bodyValue(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", properties.appKey(),
                        "appsecret", properties.appSecret()
                ))
                .retrieve()
                .bodyToMono(KisTokenResponse.class)
                .block();

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("KIS access token 발급 실패");
        }

        this.accessToken = response.accessToken();

        // 실제 expires_in 필드명은 공식 문서를 보고 맞춘다.
        // 값이 없으면 기본 23시간으로 처리한다.
        long expiresInSeconds = response.expiresIn() == null ? 23 * 60 * 60 : response.expiresIn();
        this.expiresAt = LocalDateTime.now().plusSeconds(expiresInSeconds);
    }
}
```

```java
package com.doncopy.broker.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        Long expiresIn
) {
}
```

---

## 5.4 KIS 공통 Header 생성

```java
package com.doncopy.broker.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KisHeaderFactory {

    private final KisProperties properties;
    private final KisAuthClient authClient;

    public HttpHeaders createHeaders(String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authClient.getAccessToken());
        headers.add("appkey", properties.appKey());
        headers.add("appsecret", properties.appSecret());
        headers.add("tr_id", trId);
        headers.add("custtype", "P");
        return headers;
    }
}
```

---

## 5.5 현재가 조회 예시

KIS 현재가 조회는 공식 문서에서 정확한 path와 TR ID를 확인해서 넣는다.

예시 path:

```text
/uapi/domestic-stock/v1/quotations/inquire-price
```

예시 query:

```text
FID_COND_MRKT_DIV_CODE=J
FID_INPUT_ISCD=005930
```

Java 예시:

```java
package com.doncopy.broker.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KisMarketClient {

    private static final String TR_ID_INQUIRE_PRICE = "찾아서 넣으세요";

    private final WebClient kisWebClient;
    private final KisHeaderFactory headerFactory;

    public KisCurrentPriceResponse getCurrentPrice(String stockCode) {
        return kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .headers(headers -> headers.addAll(headerFactory.createHeaders(TR_ID_INQUIRE_PRICE)))
                .retrieve()
                .bodyToMono(KisCurrentPriceResponse.class)
                .block();
    }
}
```

응답 DTO는 실제 응답 필드에 맞춰 조정한다.

```java
package com.doncopy.broker.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisCurrentPriceResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        @JsonProperty("output")
        Output output
) {
    public record Output(
            @JsonProperty("stck_prpr")
            String currentPrice,

            @JsonProperty("prdy_vrss")
            String changePrice,

            @JsonProperty("prdy_ctrt")
            String changeRate,

            @JsonProperty("acml_vol")
            String accumulatedVolume,

            @JsonProperty("acml_tr_pbmn")
            String accumulatedTradingValue
    ) {
    }
}
```

---

## 5.6 일봉 조회 예시

KIS 일자별 가격 API는 공식 문서에서 최근 일/주/월별 가격 조회 용도로 제공된다.

예시 path:

```text
/uapi/domestic-stock/v1/quotations/inquire-daily-price
```

Java 예시:

```java
package com.doncopy.broker.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KisDailyPriceClient {

    private static final String TR_ID_DAILY_PRICE = "찾아서 넣으세요";

    private final WebClient kisWebClient;
    private final KisHeaderFactory headerFactory;

    public KisDailyPriceResponse getDailyPrice(String stockCode, String periodDivCode) {
        return kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_PERIOD_DIV_CODE", periodDivCode)
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .headers(headers -> headers.addAll(headerFactory.createHeaders(TR_ID_DAILY_PRICE)))
                .retrieve()
                .bodyToMono(KisDailyPriceResponse.class)
                .block();
    }
}
```

```java
package com.doncopy.broker.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KisDailyPriceResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        @JsonProperty("output")
        List<Output> output
) {
    public record Output(
            @JsonProperty("stck_bsop_date")
            String tradeDate,

            @JsonProperty("stck_oprc")
            String openPrice,

            @JsonProperty("stck_hgpr")
            String highPrice,

            @JsonProperty("stck_lwpr")
            String lowPrice,

            @JsonProperty("stck_clpr")
            String closePrice,

            @JsonProperty("acml_vol")
            String volume,

            @JsonProperty("acml_tr_pbmn")
            String tradingValue
    ) {
    }
}
```

---

## 5.7 계좌 잔고 조회 예시

path, TR ID, query parameter는 공식 문서에서 확인 후 넣는다.

```java
package com.doncopy.broker.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KisAccountClient {

    private static final String TR_ID_BALANCE = "찾아서 넣으세요";

    private final WebClient kisWebClient;
    private final KisProperties properties;
    private final KisHeaderFactory headerFactory;

    public KisBalanceResponse getBalance() {
        return kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("찾아서 넣으세요")
                        .queryParam("CANO", extractAccountNoPrefix())
                        .queryParam("ACNT_PRDT_CD", properties.accountProductCode())
                        .queryParam("AFHR_FLPR_YN", "N")
                        .queryParam("OFL_YN", "")
                        .queryParam("INQR_DVSN", "02")
                        .queryParam("UNPR_DVSN", "01")
                        .queryParam("FUND_STTL_ICLD_YN", "N")
                        .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                        .queryParam("PRCS_DVSN", "01")
                        .queryParam("CTX_AREA_FK100", "")
                        .queryParam("CTX_AREA_NK100", "")
                        .build())
                .headers(headers -> headers.addAll(headerFactory.createHeaders(TR_ID_BALANCE)))
                .retrieve()
                .bodyToMono(KisBalanceResponse.class)
                .block();
    }

    private String extractAccountNoPrefix() {
        String accountNo = properties.accountNo();
        if (accountNo == null || accountNo.isBlank()) {
            return "찾아서 넣으세요";
        }
        return accountNo.split("-")[0];
    }
}
```

```java
package com.doncopy.broker.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KisBalanceResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        @JsonProperty("output1")
        List<PositionOutput> positions,

        @JsonProperty("output2")
        List<BalanceOutput> balances
) {
    public record PositionOutput(
            @JsonProperty("pdno")
            String stockCode,

            @JsonProperty("prdt_name")
            String stockName,

            @JsonProperty("hldg_qty")
            String quantity,

            @JsonProperty("pchs_avg_pric")
            String averageBuyPrice,

            @JsonProperty("prpr")
            String currentPrice,

            @JsonProperty("evlu_amt")
            String valuationAmount,

            @JsonProperty("evlu_pfls_amt")
            String profitLossAmount,

            @JsonProperty("evlu_pfls_rt")
            String profitLossRate
    ) {
    }

    public record BalanceOutput(
            @JsonProperty("dnca_tot_amt")
            String cashBalance,

            @JsonProperty("nass_amt")
            String totalAssetAmount,

            @JsonProperty("scts_evlu_amt")
            String stockEvaluationAmount,

            @JsonProperty("evlu_pfls_smtl_amt")
            String totalProfitLossAmount
    ) {
    }
}
```

---

## 5.8 주문 요청 예시

실제 주문 path, TR ID, 주문 구분 코드는 공식 문서를 확인해서 넣는다.

주문 실행 전 반드시 `order_request`를 먼저 생성한다.

```java
package com.doncopy.broker.kis;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KisOrderClient {

    private static final String TR_ID_BUY_ORDER = "찾아서 넣으세요";
    private static final String TR_ID_SELL_ORDER = "찾아서 넣으세요";

    private final WebClient kisWebClient;
    private final KisProperties properties;
    private final KisHeaderFactory headerFactory;

    public KisOrderResponse placeBuyOrder(KisOrderRequest request) {
        return placeOrder(TR_ID_BUY_ORDER, request);
    }

    public KisOrderResponse placeSellOrder(KisOrderRequest request) {
        return placeOrder(TR_ID_SELL_ORDER, request);
    }

    private KisOrderResponse placeOrder(String trId, KisOrderRequest request) {
        return kisWebClient.post()
                .uri("찾아서 넣으세요")
                .headers(headers -> headers.addAll(headerFactory.createHeaders(trId)))
                .bodyValue(Map.of(
                        "CANO", extractAccountNoPrefix(),
                        "ACNT_PRDT_CD", properties.accountProductCode(),
                        "PDNO", request.stockCode(),
                        "ORD_DVSN", request.orderDivisionCode(),
                        "ORD_QTY", String.valueOf(request.quantity()),
                        "ORD_UNPR", request.orderPrice()
                ))
                .retrieve()
                .bodyToMono(KisOrderResponse.class)
                .block();
    }

    private String extractAccountNoPrefix() {
        String accountNo = properties.accountNo();
        if (accountNo == null || accountNo.isBlank()) {
            return "찾아서 넣으세요";
        }
        return accountNo.split("-")[0];
    }
}
```

```java
package com.doncopy.broker.kis;

public record KisOrderRequest(
        String stockCode,
        int quantity,
        String orderPrice,
        String orderDivisionCode
) {
}
```

```java
package com.doncopy.broker.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisOrderResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        @JsonProperty("output")
        Output output
) {
    public record Output(
            @JsonProperty("KRX_FWDG_ORD_ORGNO")
            String orderOrgNo,

            @JsonProperty("ODNO")
            String orderNo,

            @JsonProperty("ORD_TMD")
            String orderTime
    ) {
    }
}
```

---

## 5.9 BrokerClient 인터페이스

서비스 레이어는 한국투자증권 구현체를 직접 호출하지 않는다.

반드시 `BrokerClient` 인터페이스를 통해 호출한다.

```java
package com.doncopy.broker;

import java.time.LocalDate;
import java.util.List;

public interface BrokerClient {

    AccountBalanceResult getAccountBalance();

    List<PortfolioPositionResult> getPositions();

    StockQuoteResult getCurrentPrice(String stockCode);

    List<DailyPriceResult> getDailyPrices(String stockCode, LocalDate from, LocalDate to);

    BrokerOrderResult placeBuyOrder(OrderCommand command);

    BrokerOrderResult placeSellOrder(OrderCommand command);

    BrokerOrderStatusResult getOrderStatus(String brokerOrderNo);

    List<ExecutionResult> getExecutions(LocalDate date);
}
```

```java
package com.doncopy.broker.kis;

import com.doncopy.broker.BrokerClient;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KisBrokerClient implements BrokerClient {

    private final KisAccountClient accountClient;
    private final KisMarketClient marketClient;
    private final KisDailyPriceClient dailyPriceClient;
    private final KisOrderClient orderClient;

    @Override
    public AccountBalanceResult getAccountBalance() {
        KisBalanceResponse response = accountClient.getBalance();
        return KisMapper.toAccountBalanceResult(response);
    }

    @Override
    public List<PortfolioPositionResult> getPositions() {
        KisBalanceResponse response = accountClient.getBalance();
        return KisMapper.toPortfolioPositions(response);
    }

    @Override
    public StockQuoteResult getCurrentPrice(String stockCode) {
        KisCurrentPriceResponse response = marketClient.getCurrentPrice(stockCode);
        return KisMapper.toStockQuoteResult(response);
    }

    @Override
    public List<DailyPriceResult> getDailyPrices(String stockCode, LocalDate from, LocalDate to) {
        KisDailyPriceResponse response = dailyPriceClient.getDailyPrice(stockCode, "D");
        return KisMapper.toDailyPrices(response);
    }

    @Override
    public BrokerOrderResult placeBuyOrder(OrderCommand command) {
        KisOrderRequest request = KisMapper.toKisOrderRequest(command);
        KisOrderResponse response = orderClient.placeBuyOrder(request);
        return KisMapper.toBrokerOrderResult(response);
    }

    @Override
    public BrokerOrderResult placeSellOrder(OrderCommand command) {
        KisOrderRequest request = KisMapper.toKisOrderRequest(command);
        KisOrderResponse response = orderClient.placeSellOrder(request);
        return KisMapper.toBrokerOrderResult(response);
    }

    @Override
    public BrokerOrderStatusResult getOrderStatus(String brokerOrderNo) {
        throw new UnsupportedOperationException("공식 문서 확인 후 구현하세요");
    }

    @Override
    public List<ExecutionResult> getExecutions(LocalDate date) {
        throw new UnsupportedOperationException("공식 문서 확인 후 구현하세요");
    }
}
```

---

# 6. OpenDART API 연동

## 6.1 구현 원칙

OpenDART는 공시와 재무제표 수집에 사용한다.

구현 대상:

```text
corpCode.xml 다운로드
공시 목록 조회
기업 개황 조회
단일회사 주요계정 재무제표 조회
주요 이벤트 공시 분류
```

주의:

```text
OpenDART corpCode.xml은 ZIP/XML 형태로 내려올 수 있다.
corp_code와 stock_code 매핑을 dart_corp_code에 저장한다.
공시 목록은 receipt_no 기준으로 중복 제거한다.
OpenDART status가 000이 아니면 실패로 처리한다.
요청 제한 초과나 시스템 점검 응답은 재시도 대상이다.
```

---

## 6.2 DART 설정 Properties

```java
package com.doncopy.dart.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.dart")
public record DartProperties(
        String baseUrl,
        String apiKey
) {
}
```

`PropertiesConfig`에 추가한다.

```java
@EnableConfigurationProperties({
        KisProperties.class,
        DartProperties.class
})
```

---

## 6.3 corpCode.xml 다운로드 예시

요청 예시:

```http
GET /api/corpCode.xml?crtfc_key=찾아서 넣으세요
```

구현 예시:

```java
package com.doncopy.dart.client;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class DartCorpCodeClient {

    private final WebClient dartWebClient;
    private final DartProperties properties;

    public byte[] downloadCorpCodeZip() {
        return dartWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/corpCode.xml")
                        .queryParam("crtfc_key", properties.apiKey())
                        .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }
}
```

ZIP/XML 파싱 예시:

```java
package com.doncopy.dart.client;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class DartCorpCodeZipParser {

    public String unzipXml(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("DART corpCode.xml ZIP 파싱 실패", e);
        }

        throw new IllegalStateException("DART corpCode.xml 내부 XML 파일 없음");
    }
}
```

---

## 6.4 공시 목록 조회 예시

요청 예시:

```http
GET /api/list.json?crtfc_key=찾아서 넣으세요&corp_code=00126380&bgn_de=20260101&end_de=20260527
```

Java 예시:

```java
package com.doncopy.dart.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class DartDisclosureClient {

    private final WebClient dartWebClient;
    private final DartProperties properties;

    public DartDisclosureListResponse getDisclosureList(
            String corpCode,
            String beginDate,
            String endDate
    ) {
        return dartWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/list.json")
                        .queryParam("crtfc_key", properties.apiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", beginDate)
                        .queryParam("end_de", endDate)
                        .queryParam("last_reprt_at", "N")
                        .build())
                .retrieve()
                .bodyToMono(DartDisclosureListResponse.class)
                .block();
    }
}
```

```java
package com.doncopy.dart.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DartDisclosureListResponse(
        String status,
        String message,
        List<DartDisclosureItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public record DartDisclosureItem(
            @JsonProperty("corp_code")
            String corpCode,

            @JsonProperty("corp_name")
            String corpName,

            @JsonProperty("stock_code")
            String stockCode,

            @JsonProperty("corp_cls")
            String corpClass,

            @JsonProperty("report_nm")
            String reportName,

            @JsonProperty("rcept_no")
            String receiptNo,

            @JsonProperty("flr_nm")
            String submitter,

            @JsonProperty("rcept_dt")
            String receiptDate,

            @JsonProperty("rm")
            String remark
    ) {
    }
}
```

---

## 6.5 단일회사 주요계정 재무제표 조회 예시

요청 예시:

```http
GET /api/fnlttSinglAcnt.json?crtfc_key=찾아서 넣으세요&corp_code=00126380&bsns_year=2025&reprt_code=11011
```

보고서 코드:

```text
11013 = 1분기보고서
11012 = 반기보고서
11014 = 3분기보고서
11011 = 사업보고서
```

Java 예시:

```java
package com.doncopy.dart.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class DartFinancialClient {

    private final WebClient dartWebClient;
    private final DartProperties properties;

    public DartFinancialStatementResponse getSingleCompanyFinancialStatement(
            String corpCode,
            int businessYear,
            String reportCode
    ) {
        return dartWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fnlttSinglAcnt.json")
                        .queryParam("crtfc_key", properties.apiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", businessYear)
                        .queryParam("reprt_code", reportCode)
                        .build())
                .retrieve()
                .bodyToMono(DartFinancialStatementResponse.class)
                .block();
    }
}
```

```java
package com.doncopy.dart.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DartFinancialStatementResponse(
        String status,
        String message,
        List<FinancialItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public record FinancialItem(
            @JsonProperty("rcept_no")
            String receiptNo,

            @JsonProperty("reprt_code")
            String reportCode,

            @JsonProperty("bsns_year")
            String businessYear,

            @JsonProperty("corp_code")
            String corpCode,

            @JsonProperty("stock_code")
            String stockCode,

            @JsonProperty("fs_div")
            String financialStatementDivision,

            @JsonProperty("fs_nm")
            String financialStatementName,

            @JsonProperty("sj_div")
            String statementDivision,

            @JsonProperty("sj_nm")
            String statementName,

            @JsonProperty("account_nm")
            String accountName,

            @JsonProperty("thstrm_amount")
            String currentTermAmount,

            @JsonProperty("frmtrm_amount")
            String previousTermAmount,

            @JsonProperty("currency")
            String currency
    ) {
    }
}
```

---

# 7. 네이버 뉴스 검색 API 연동

## 7.1 구현 원칙

네이버 뉴스 검색 API는 종목별 뉴스 수집에 사용한다.

구현 대상:

```text
종목명 검색
회사명 검색
업종 키워드 검색
뉴스 제목/요약/URL/발행일 저장
origin_url_hash 기반 중복 제거
```

주의:

```text
Client ID/Secret은 HTTP Header로 전달한다.
뉴스 중복 제거는 origin_url_hash로 처리한다.
HTML 태그가 포함될 수 있으므로 title/description 정제 로직을 둔다.
날짜 파싱 실패 시 published_at은 null 허용한다.
```

---

## 7.2 Naver 설정 Properties

```java
package com.doncopy.news.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.naver")
public record NaverProperties(
        String baseUrl,
        String clientId,
        String clientSecret
) {
}
```

`PropertiesConfig`에 추가한다.

```java
@EnableConfigurationProperties({
        KisProperties.class,
        DartProperties.class,
        NaverProperties.class
})
```

---

## 7.3 뉴스 검색 요청 예시

요청 예시:

```http
GET /v1/search/news.json?query=삼성전자&display=10&start=1&sort=date
X-Naver-Client-Id: 찾아서 넣으세요
X-Naver-Client-Secret: 찾아서 넣으세요
```

Java 예시:

```java
package com.doncopy.news.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class NaverNewsClient {

    private final WebClient naverWebClient;
    private final NaverProperties properties;

    public NaverNewsSearchResponse searchNews(String keyword, int display, int start) {
        return naverWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/search/news.json")
                        .queryParam("query", keyword)
                        .queryParam("display", display)
                        .queryParam("start", start)
                        .queryParam("sort", "date")
                        .build())
                .header("X-Naver-Client-Id", properties.clientId())
                .header("X-Naver-Client-Secret", properties.clientSecret())
                .retrieve()
                .bodyToMono(NaverNewsSearchResponse.class)
                .block();
    }
}
```

```java
package com.doncopy.news.client;

import java.util.List;

public record NaverNewsSearchResponse(
        String lastBuildDate,
        int total,
        int start,
        int display,
        List<NaverNewsItem> items
) {
    public record NaverNewsItem(
            String title,
            String originallink,
            String link,
            String description,
            String pubDate
    ) {
    }
}
```

---

## 7.4 뉴스 정제/해시 유틸

```java
package com.doncopy.news.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.web.util.HtmlUtils;

public final class NewsUtils {

    private NewsUtils() {
    }

    public static String cleanHtml(String value) {
        if (value == null) {
            return null;
        }

        String unescaped = HtmlUtils.htmlUnescape(value);
        return unescaped.replaceAll("<[^>]*>", "").trim();
    }

    public static String sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("hash 대상 값이 null입니다.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 생성 실패", e);
        }
    }
}
```

---

## 7.5 stock_news 저장 흐름

```java
package com.doncopy.news.service;

import com.doncopy.news.client.NaverNewsClient;
import com.doncopy.news.client.NaverNewsSearchResponse;
import com.doncopy.news.util.NewsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewsCollectService {

    private final NaverNewsClient naverNewsClient;
    private final StockNewsMapper stockNewsMapper;

    public void collectStockNews(String stockCode, String keyword) {
        NaverNewsSearchResponse response = naverNewsClient.searchNews(keyword, 10, 1);

        for (NaverNewsSearchResponse.NaverNewsItem item : response.items()) {
            String originUrl = item.originallink() != null ? item.originallink() : item.link();
            String originUrlHash = NewsUtils.sha256(originUrl);

            StockNews news = StockNews.builder()
                    .stockCode(stockCode)
                    .keyword(keyword)
                    .title(NewsUtils.cleanHtml(item.title()))
                    .summary(NewsUtils.cleanHtml(item.description()))
                    .originUrl(originUrl)
                    .originUrlHash(originUrlHash)
                    .naverLink(item.link())
                    .publishedAt(DateParser.parseNaverPubDate(item.pubDate()))
                    .rawJson(item)
                    .build();

            stockNewsMapper.insertIgnore(news);
        }
    }
}
```

MyBatis 예시:

```xml
<insert id="insertIgnore" parameterType="com.doncopy.news.domain.StockNews">
    INSERT IGNORE INTO stock_news (
        stock_code,
        keyword,
        title,
        summary,
        origin_url,
        origin_url_hash,
        naver_link,
        publisher,
        published_at,
        raw_json
    ) VALUES (
        #{stockCode},
        #{keyword},
        #{title},
        #{summary},
        #{originUrl},
        #{originUrlHash},
        #{naverLink},
        #{publisher},
        #{publishedAt},
        CAST(#{rawJson} AS JSON)
    )
</insert>
```

주의:

```text
raw_json 컬럼에 객체를 바로 넣을 수 없으면 JSON 문자열로 변환한 뒤 저장한다.
TypeHandler가 필요하면 먼저 사용자에게 추가 구현 계획을 보고한다.
```

---

# 8. OpenAI API 연동

## 8.1 구현 원칙

OpenAI API는 AI 판단 엔진으로 사용한다.

구현 대상:

```text
종목 분석
뉴스 요약
공시 요약
기술적 지표 해석
매수/매도/보유 판단
목표가 판단
손절가 판단
예상 보유 기간 판단
추천 매수 비중 판단
리스크 요인 분석
피드백 리포트 생성
```

주의:

```text
AI 응답은 반드시 JSON Schema 기반 구조화 응답으로 받는다.
JSON 파싱 실패 시 ai_decision은 생성하지 않는다.
원문 응답은 ai_decision_raw_response에 저장한다.
프롬프트는 ai_prompt_log에 저장한다.
AI가 BUY라고 해도 직접 주문하지 않는다.
```

---

## 8.2 OpenAI 설정 Properties

```java
package com.doncopy.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.openai")
public record OpenAiProperties(
        String baseUrl,
        String apiKey,
        String model
) {
}
```

`PropertiesConfig`에 추가한다.

```java
@EnableConfigurationProperties({
        KisProperties.class,
        DartProperties.class,
        NaverProperties.class,
        OpenAiProperties.class
})
```

---

## 8.3 OpenAI Structured Output 요청 예시

Responses API 예시 구조:

```java
package com.doncopy.ai.client;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class OpenAiDecisionClient {

    private final WebClient openAiWebClient;
    private final OpenAiProperties properties;

    public OpenAiResponse createDecision(String systemPrompt, String userPrompt) {
        Map<String, Object> request = Map.of(
                "model", properties.model(),
                "input", List.of(
                        Map.of(
                                "role", "system",
                                "content", systemPrompt
                        ),
                        Map.of(
                                "role", "user",
                                "content", userPrompt
                        )
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "stock_trade_decision",
                                "schema", stockDecisionSchema(),
                                "strict", true
                        )
                )
        );

        return openAiWebClient.post()
                .uri("/v1/responses")
                .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .block();
    }

    private Map<String, Object> stockDecisionSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of(
                        "stockCode",
                        "stockName",
                        "decision",
                        "confidence",
                        "currentPrice",
                        "targetPrice",
                        "stopLossPrice",
                        "expectedReturnRate",
                        "expectedLossRate",
                        "riskRewardRatio",
                        "recommendedPortfolioWeight",
                        "expectedHoldingDays",
                        "reason"
                ),
                "properties", Map.of(
                        "stockCode", Map.of("type", "string"),
                        "stockName", Map.of("type", "string"),
                        "decision", Map.of(
                                "type", "string",
                                "enum", List.of("BUY", "SELL", "HOLD")
                        ),
                        "confidence", Map.of(
                                "type", "number",
                                "minimum", 0,
                                "maximum", 1
                        ),
                        "currentPrice", Map.of("type", "number"),
                        "targetPrice", Map.of("type", "number"),
                        "stopLossPrice", Map.of("type", "number"),
                        "expectedReturnRate", Map.of("type", "number"),
                        "expectedLossRate", Map.of("type", "number"),
                        "riskRewardRatio", Map.of("type", "number"),
                        "recommendedPortfolioWeight", Map.of(
                                "type", "number",
                                "minimum", 0,
                                "maximum", 1
                        ),
                        "expectedHoldingDays", Map.of("type", "integer"),
                        "reason", Map.of("type", "string")
                )
        );
    }
}
```

OpenAI 응답 DTO는 실제 응답 구조에 맞춰 조정한다.

```java
package com.doncopy.ai.client;

import java.util.List;

public record OpenAiResponse(
        String id,
        String status,
        List<Output> output,
        Usage usage
) {
    public record Output(
            String type,
            String id,
            String status,
            String role,
            List<Content> content
    ) {
    }

    public record Content(
            String type,
            String text
    ) {
    }

    public record Usage(
            Integer input_tokens,
            Integer output_tokens,
            Integer total_tokens
    ) {
    }

    public String extractText() {
        if (output == null) {
            return null;
        }

        return output.stream()
                .filter(item -> item.content() != null)
                .flatMap(item -> item.content().stream())
                .filter(content -> content.text() != null)
                .map(Content::text)
                .findFirst()
                .orElse(null);
    }
}
```

---

## 8.4 AI 판단 파싱 DTO

```java
package com.doncopy.ai.domain;

import java.math.BigDecimal;

public record AiTradeDecisionJson(
        String stockCode,
        String stockName,
        String decision,
        BigDecimal confidence,
        BigDecimal currentPrice,
        BigDecimal targetPrice,
        BigDecimal stopLossPrice,
        BigDecimal expectedReturnRate,
        BigDecimal expectedLossRate,
        BigDecimal riskRewardRatio,
        BigDecimal recommendedPortfolioWeight,
        Integer expectedHoldingDays,
        String reason
) {
}
```

파싱 예시:

```java
package com.doncopy.ai.parser;

import com.doncopy.ai.domain.AiTradeDecisionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiDecisionParser {

    private final ObjectMapper objectMapper;

    public AiTradeDecisionJson parse(String responseText) {
        try {
            return objectMapper.readValue(responseText, AiTradeDecisionJson.class);
        } catch (Exception e) {
            throw new IllegalStateException("AI 판단 JSON 파싱 실패", e);
        }
    }
}
```

---

## 8.5 AI 판단 프롬프트 예시

```java
package com.doncopy.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class AiDecisionPromptBuilder {

    public String buildSystemPrompt() {
        return """
                너는 한국 주식 자동매매 시스템의 AI 판단 엔진이다.

                반드시 JSON Schema에 맞는 응답만 반환한다.
                마크다운, 설명문, 코드블록은 절대 포함하지 않는다.

                너는 매수/매도/보유 판단을 한다.
                너는 목표가와 손절가를 직접 판단한다.
                너는 예상 보유 기간과 추천 매수 비중도 판단한다.

                단, 너의 판단이 직접 주문으로 이어지지는 않는다.
                최종 주문은 Risk Manager와 Order Policy Engine 검증 후 실행된다.

                판단 기준:
                - 기술적 지표
                - 뉴스
                - 공시
                - 재무 정보
                - 현재 가격
                - 거래량
                - 리스크 대비 기대수익률

                보수적으로 판단하라.
                데이터가 부족하면 HOLD를 선택하라.
                """;
    }

    public String buildUserPrompt(String stockAnalysisInputJson) {
        return """
                아래 종목 데이터를 분석해서 매매 판단 JSON을 생성하라.

                입력 데이터:
                %s
                """.formatted(stockAnalysisInputJson);
    }
}
```

---

# 9. 구현 우선순위

Copilot Agent는 아래 순서대로 구현한다.

```text
1. application.yml placeholder 추가
2. Properties 클래스 생성
3. WebClientConfig 생성
4. KIS Access Token 발급 구현
5. KIS 현재가 조회 구현
6. KIS 일봉 조회 구현
7. KIS 계좌 잔고 조회 구현
8. OpenDART corpCode.xml 다운로드 구현
9. OpenDART 공시 목록 조회 구현
10. 네이버 뉴스 검색 구현
11. stock_news origin_url_hash 저장 구현
12. OpenAI Structured Output 요청 구현
13. AI 판단 JSON 파싱 구현
14. 외부 API 호출 로그 저장 구현
```

---

# 10. 구현 시 금지사항

```text
API Key를 코드에 직접 넣지 않는다.
계좌번호를 코드에 직접 넣지 않는다.
확실하지 않은 KIS TR ID를 임의로 넣지 않는다.
공식 문서 확인이 필요한 값은 "찾아서 넣으세요"로 둔다.
DB 스키마를 임의로 변경하지 않는다.
DB 스키마 변경이 필요하면 반드시 사용자에게 먼저 보고한다.
주문 API를 바로 실전 호출하지 않는다.
처음에는 PAPER 모드로 구현한다.
```

---

# 11. DB 스키마 변경 필요 시 보고 양식

DB 변경이 필요하면 아래 형식으로 사용자에게 먼저 보고한다.

```md
## DB 스키마 변경 제안

### 변경 필요 이유

...

### 변경 대상

- table:
- column:
- index:

### 변경 전

```sql
...
```

### 변경 후

```sql
...
```

### 영향 범위

- Mapper:
- Service:
- Scheduler:
- API:
- 기존 데이터 영향:

### 승인 요청

이 변경을 적용해도 되는지 확인 필요.
```

승인 전까지는 변경하지 않는다.

---

# 12. 참고 구현 방향

초기에는 실제 주문보다 아래 흐름을 먼저 완성한다.

```text
현재가 조회
→ 일봉 저장
→ 뉴스 저장
→ 공시 저장
→ AI 판단 생성
→ ai_decision 저장
→ RiskManager 검증
→ order_request 생성
→ PAPER 모드 가상 체결
→ ai_feedback 저장
```

이 흐름이 완성되기 전까지 실전 주문 API는 호출하지 않는다.
