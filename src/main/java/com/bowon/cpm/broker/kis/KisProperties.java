package com.bowon.cpm.broker.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한국투자증권 Open API 설정
 * application.yml의 external.kis.* 값과 바인딩된다.
 *
 * 실전투자 base-url: https://openapi.koreainvestment.com:9443
 * (모의투자는 사용하지 않음)
 */
@ConfigurationProperties(prefix = "external.kis")
public record KisProperties(
        /** 실전투자 REST API 기본 URL */
        String baseUrl,

        /**
         * TODO: WebSocket 실시간 시세 수신 시 사용 (미구현)
         * KIS WebSocket URL: wss://openapi.koreainvestment.com:9443
         * 사용 목적: 실시간 현재가(H0STCNT0), 호가(H0STASP0), 체결통보(H0STCNI9)
         */
        String websocketUrl,

        /** KIS Open API 앱 키 (KIS 개발자센터에서 발급) */
        String appKey,

        /** KIS Open API 앱 시크릿 (KIS 개발자센터에서 발급) */
        String appSecret,

        /**
         * 계좌번호 (형식: XXXXXXXXXX-XX)
         * 예) 50123456-01
         * 앞부분(CANO): 계좌번호 8~10자리
         * 뒷부분(ACNT_PRDT_CD): 상품 코드 (보통 01)
         */
        String accountNo,

        /**
         * 계좌 상품 코드
         * 01 = 종합계좌 (일반적으로 01 사용)
         */
        String accountProductCode,

        /**
         * Access Token 발급 경로
         * POST {baseUrl}{tokenPath} 로 토큰을 발급한다.
         * 기본값: /oauth2/tokenP
         */
        String tokenPath,

        /**
         * TODO: WebSocket 접속키(approval_key) 발급 경로 (미구현)
         * WebSocket 연결 전 접속키를 먼저 발급해야 한다.
         */
        String approvalKeyPath
) {
}
