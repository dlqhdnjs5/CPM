package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.domain.BrokerToken;
import com.bowon.cpm.broker.kis.dto.KisTokenResponse;
import com.bowon.cpm.broker.mapper.BrokerTokenMapper;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * KIS OAuth2 Access Token 관리
 *
 * ⚠️ KIS는 하루에 발급 가능한 토큰 수에 제한이 있다.
 *    서버 재시작 후에도 기존 토큰을 재사용해야 한다.
 *    → 토큰을 DB(broker_token)에 저장하여 서버 재시작/재배포 후에도 복원한다.
 *
 * 우선순위:
 *   1) 메모리 캐시 (가장 빠름)
 *   2) DB 캐시 (broker_token 테이블, 재시작 후 복원)
 *   3) KIS API 신규 발급 (마지막)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisAuthClient {

    private static final String BROKER_TYPE = "KIS";
    private static final String TOKEN_TYPE  = "ACCESS";
    /** 만료 10분 전부터는 재발급 대상 */
    private static final long EXPIRE_BUFFER_MINUTES = 10;

    private final WebClient kisWebClient;
    private final KisProperties properties;
    private final BrokerTokenMapper brokerTokenMapper;

    /** 메모리 캐시 */
    private volatile String accessToken;
    private volatile LocalDateTime expiresAt;

    /**
     * Access Token 반환
     * 1. 메모리 캐시 확인
     * 2. DB 캐시 확인 (서버 재시작 후 복원)
     * 3. 둘 다 없거나 만료됐으면 KIS API 호출
     */
    public String getAccessToken() {
        // 1. 메모리에 유효한 토큰이 있으면 바로 사용
        if (isTokenValid()) {
            return accessToken;
        }
        // 2. DB에서 토큰 복원 시도
        loadFromDb();
        if (isTokenValid()) {
            log.info("[KIS] DB에서 Access Token 복원 완료. 만료: {}", expiresAt);
            return accessToken;
        }
        // 3. 신규 발급
        issueAccessToken();
        return accessToken;
    }

    /**
     * Access Token 신규 발급 (synchronized — 동시 중복 발급 방지)
     *
     * 요청 Body:
     * - grant_type : "client_credentials" (OAuth2 클라이언트 자격증명 방식, 고정값)
     * - appkey     : KIS 앱 키
     * - appsecret  : KIS 앱 시크릿
     *
     * 응답:
     * - access_token        : 실제 토큰 문자열
     * - expires_in          : 유효기간 (초 단위, 보통 86400 = 24시간)
     * - token_type          : "Bearer"
     */
    public synchronized void issueAccessToken() {
        // synchronized 진입 후 한 번 더 확인 — 다른 스레드가 이미 발급했을 수 있음
        if (isTokenValid()) return;

        // DB도 한 번 더 체크 — 다른 인스턴스가 방금 발급했을 수 있음 (KIS 일일 발급 제한 회피)
        loadFromDb();
        if (isTokenValid()) {
            log.info("[KIS] DB에 최신 토큰 존재. 신규 발급 생략. 만료: {}", expiresAt);
            return;
        }

        log.info("[KIS] Access Token 발급 요청");

        try {
            KisTokenResponse response = kisWebClient.post()
                    .uri(properties.tokenPath())  // /oauth2/tokenP
                    .bodyValue(Map.of(
                            "grant_type", "client_credentials", // OAuth2 고정값
                            "appkey", properties.appKey(),       // KIS 앱 키
                            "appsecret", properties.appSecret()  // KIS 앱 시크릿
                    ))
                    .retrieve()
                    .bodyToMono(KisTokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new ExternalApiException("KIS", "Access Token 발급 실패: 응답 없음");
            }

            LocalDateTime now = LocalDateTime.now();
            // expires_in 없으면 기본 24시간으로 처리
            long expiresInSeconds = response.expiresIn() != null ? response.expiresIn() : 86400L;

            this.accessToken = response.accessToken();
            this.expiresAt = now.plusSeconds(expiresInSeconds);

            // DB에 upsert 저장 (서버 재시작/재배포 후 재사용)
            saveToDb(now);

            log.info("[KIS] Access Token 발급 완료. 만료: {}", this.expiresAt);

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "Access Token 발급 중 오류: " + e.getMessage());
        }
    }

    private boolean isTokenValid() {
        return accessToken != null && expiresAt != null
                && LocalDateTime.now().isBefore(expiresAt.minusMinutes(EXPIRE_BUFFER_MINUTES));
    }

    /** 토큰을 DB에 upsert */
    private void saveToDb(LocalDateTime issuedAt) {
        try {
            BrokerToken token = BrokerToken.builder()
                    .brokerType(BROKER_TYPE)
                    .tokenType(TOKEN_TYPE)
                    .accessToken(accessToken)
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .build();
            brokerTokenMapper.upsert(token);
            log.debug("[KIS] Access Token DB 저장 완료");
        } catch (Exception e) {
            // DB 저장 실패해도 방금 발급한 토큰은 메모리에 있으므로 즉시 사용은 가능
            log.warn("[KIS] Access Token DB 저장 실패 (무시, 메모리 캐시 사용): {}", e.getMessage());
        }
    }

    /** DB에서 토큰 복원 */
    private void loadFromDb() {
        try {
            Optional<BrokerToken> opt = brokerTokenMapper.findByBrokerAndType(BROKER_TYPE, TOKEN_TYPE);
            if (opt.isEmpty()) return;

            BrokerToken saved = opt.get();
            if (saved.getAccessToken() == null || saved.getExpiresAt() == null) return;

            if (LocalDateTime.now().isBefore(saved.getExpiresAt().minusMinutes(EXPIRE_BUFFER_MINUTES))) {
                this.accessToken = saved.getAccessToken();
                this.expiresAt = saved.getExpiresAt();
            } else {
                log.debug("[KIS] DB의 토큰이 만료됨. 신규 발급 필요.");
            }
        } catch (Exception e) {
            log.warn("[KIS] Access Token DB 조회 실패 (무시): {}", e.getMessage());
        }
    }
}
