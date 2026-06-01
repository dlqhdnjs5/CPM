package com.bowon.cpm.dart.client;

import com.bowon.cpm.common.exception.ExternalApiException;
import com.bowon.cpm.dart.client.dto.DartFinancialResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * DART 단일회사 주요계정 재무제표 조회
 *
 * API: GET /api/fnlttSinglAcnt.json
 * - corp_code  : 기업 고유번호
 * - bsns_year  : 사업연도
 * - reprt_code : 11011=사업보고서, 11012=반기, 11013=1분기, 11014=3분기
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartFinancialClient {

    private final WebClient dartWebClient;

    @Value("${external.dart.api-key}")
    private String apiKey;

    public DartFinancialResponse getFinancialStatement(
            String corpCode, int businessYear, String reportCode) {

        log.debug("[DART] 재무제표 조회: corpCode={}, year={}, report={}", corpCode, businessYear, reportCode);

        try {
            DartFinancialResponse response = dartWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/fnlttSinglAcnt.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", String.valueOf(businessYear))
                            .queryParam("reprt_code", reportCode)
                            .build())
                    .retrieve()
                    .bodyToMono(DartFinancialResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("DART", "재무제표 응답 없음: corpCode=" + corpCode);
            }
            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("DART", "재무제표 조회 오류: " + e.getMessage());
        }
    }
}

