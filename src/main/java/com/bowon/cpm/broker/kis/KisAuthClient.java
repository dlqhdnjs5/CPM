package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.kis.dto.KisTokenResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * KIS OAuth2 Access Token 관리
 *
 * ⚠️ KIS는 하루에 발급 가능한 토큰 수에 제한이 있다.
 *    서버 재시작 후에도 기존 토큰을 재사용해야 한다.
 *    → 토큰을 파일(.kis_token)에 저장하여 서버 재시작 후에도 복원한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisAuthClient {

    private final WebClient kisWebClient;
    private final KisProperties properties;

    /** 토큰 저장 파일 경로 (서버 재시작 후에도 복원용) */
    private static final Path TOKEN_FILE = Paths.get(System.getProperty("user.home"), ".kis_token");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 메모리 캐시 */
    private volatile String accessToken;
    private volatile LocalDateTime expiresAt;

    /**
     * Access Token 반환
     * 1. 메모리 캐시 확인
     * 2. 파일 캐시 확인 (서버 재시작 후 복원)
     * 3. 둘 다 없거나 만료됐으면 KIS API 호출
     */
    public String getAccessToken() {
        // 메모리에 유효한 토큰이 있으면 바로 사용
        if (isTokenValid()) {
            return accessToken;
        }
        // 파일에서 토큰 복원 시도
        loadFromFile();
        if (isTokenValid()) {
            log.info("[KIS] 파일에서 Access Token 복원 완료. 만료: {}", expiresAt);
            return accessToken;
        }
        // 신규 발급
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
        if (isTokenValid()) return;

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

            this.accessToken = response.accessToken();
            // expires_in 없으면 기본 24시간으로 처리
            long expiresInSeconds = response.expiresIn() != null ? response.expiresIn() : 86400L;
            this.expiresAt = LocalDateTime.now().plusSeconds(expiresInSeconds);

            // 파일에 저장 (서버 재시작 후 재사용)
            saveToFile();

            log.info("[KIS] Access Token 발급 완료. 만료: {}", this.expiresAt);

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "Access Token 발급 중 오류: " + e.getMessage());
        }
    }

    private boolean isTokenValid() {
        return accessToken != null && expiresAt != null
                && LocalDateTime.now().isBefore(expiresAt.minusMinutes(10));
    }

    /** 토큰을 파일에 저장 (token\n만료시각 형식) */
    private void saveToFile() {
        try {
            String content = accessToken + "\n" + expiresAt.format(FMT);
            Files.writeString(TOKEN_FILE, content, StandardCharsets.UTF_8);
            log.debug("[KIS] Access Token 파일 저장: {}", TOKEN_FILE);
        } catch (IOException e) {
            log.warn("[KIS] Access Token 파일 저장 실패 (무시): {}", e.getMessage());
        }
    }

    /** 파일에서 토큰 복원 */
    private void loadFromFile() {
        try {
            if (!Files.exists(TOKEN_FILE)) return;
            String content = Files.readString(TOKEN_FILE, StandardCharsets.UTF_8).trim();
            String[] lines = content.split("\n");
            if (lines.length < 2) return;

            String savedToken = lines[0].trim();
            LocalDateTime savedExpiry = LocalDateTime.parse(lines[1].trim(), FMT);

            if (LocalDateTime.now().isBefore(savedExpiry.minusMinutes(10))) {
                this.accessToken = savedToken;
                this.expiresAt = savedExpiry;
            } else {
                log.debug("[KIS] 파일의 토큰이 만료됨. 신규 발급 필요.");
            }
        } catch (Exception e) {
            log.warn("[KIS] Access Token 파일 읽기 실패 (무시): {}", e.getMessage());
        }
    }
}
