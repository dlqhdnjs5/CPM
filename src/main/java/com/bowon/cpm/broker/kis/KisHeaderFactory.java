package com.bowon.cpm.broker.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * KIS API 공통 요청 헤더 생성기
 *
 * KIS Open API는 모든 REST 요청에 아래 헤더가 필요하다.
 * 이 클래스가 헤더를 한 곳에서 조립해준다.
 *
 * 필수 헤더 목록:
 * - Authorization : "Bearer {access_token}"  → OAuth2 인증 토큰
 * - appkey        : KIS 앱 키                → 앱 식별용
 * - appsecret     : KIS 앱 시크릿            → 앱 인증용
 * - tr_id         : 거래 ID (TR ID)          → 어떤 API를 호출하는지 구분하는 코드
 *                   예) FHKST01010100 = 주식현재가, TTTC8434R = 잔고조회
 * - custtype      : 고객 유형
 *                   "P" = 개인 (Personal), "B" = 법인 (Business)
 */
@Component
@RequiredArgsConstructor
public class KisHeaderFactory {

    private final KisProperties properties;
    private final KisAuthClient authClient;

    /**
     * API별 요청 헤더 생성
     *
     * @param trId KIS TR ID (거래 구분 코드) — API마다 다름
     * @return 완성된 HttpHeaders
     */
    public HttpHeaders createHeaders(String trId) {
        HttpHeaders headers = new HttpHeaders();
        // OAuth2 Bearer 토큰 (만료 시 자동 갱신됨)
        headers.setBearerAuth(authClient.getAccessToken());
        // 앱 키/시크릿 — KIS가 요청 출처를 검증하는 데 사용
        headers.set("appkey", properties.appKey());
        headers.set("appsecret", properties.appSecret());
        // TR ID — KIS가 어떤 API인지 라우팅하는 데 사용
        headers.set("tr_id", trId);
        // 고객 유형 — 개인이므로 "P" 고정
        headers.set("custtype", "P");
        return headers;
    }
}
