package com.bowon.cpm.dart.client;

import com.bowon.cpm.dart.client.dto.DartDisclosureResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * DART 공시 목록 조회 클라이언트
 *
 * API: GET /api/list.json
 *
 * Query Parameter 설명:
 * - crtfc_key  : DART API 인증키
 * - corp_code  : DART 기업 고유번호 (8자리)
 * - bgn_de     : 시작일 (yyyyMMdd)
 * - end_de     : 종료일 (yyyyMMdd)
 * - last_reprt_at : 최종보고서 여부 ("Y"=최종, "N"=전체)
 *
 * 응답 status:
 * - "000" = 정상
 * - "010" = 등록되지 않은 키
 * - "020" = 요청 제한 초과
 * - "100" = 조회 결과 없음 (오류 아님)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartDisclosureClient {

    private final WebClient dartWebClient;

    @Value("${external.dart.api-key}")
    private String apiKey;

    /**
     * 기업 공시 목록 조회
     *
     * @param corpCode  DART 기업 고유번호
     * @param beginDate 시작일 (yyyyMMdd)
     * @param endDate   종료일 (yyyyMMdd)
     */
    public DartDisclosureResponse getDisclosureList(
            String corpCode, String beginDate, String endDate) {

        log.debug("[DART] 공시 목록 조회: corpCode={}, {} ~ {}", corpCode, beginDate, endDate);

        try {
            DartDisclosureResponse response = dartWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/list.json")
                            .queryParam("crtfc_key", apiKey)      // DART 인증키
                            .queryParam("corp_code", corpCode)    // 기업 고유번호
                            .queryParam("bgn_de", beginDate)      // 시작일
                            .queryParam("end_de", endDate)        // 종료일
                            .queryParam("last_reprt_at", "N")     // 전체 보고서
                            .queryParam("page_count", "100")      // 최대 100건
                            .build())
                    .retrieve()
                    .bodyToMono(DartDisclosureResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("DART", "공시 목록 응답 없음: corpCode=" + corpCode);
            }

            // "100" = 조회 결과 없음 (오류 아님, 빈 리스트 처리)
            if ("100".equals(response.status())) {
                log.debug("[DART] 공시 없음: corpCode={}", corpCode);
                return response;
            }

            if (!response.isSuccess()) {
                throw new ExternalApiException("DART",
                        "공시 목록 조회 실패: corpCode=" + corpCode + ", status=" + response.status()
                                + ", msg=" + response.message());
            }

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("DART", "공시 목록 조회 중 오류: " + e.getMessage());
        }
    }
}

