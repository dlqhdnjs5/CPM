package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.kis.dto.KisCurrentPriceResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * KIS 주식 시세 조회 클라이언트
 *
 * API: GET /uapi/domestic-stock/v1/quotations/inquire-price
 * TR_ID: FHKST01010100 (실전투자 — 주식현재가시세)
 *
 * Query Parameter 설명:
 * - FID_COND_MRKT_DIV_CODE : 시장 구분 코드
 *      "J" = 주식/ETF/ETN (KRX 정규시장)
 *      "W" = ELW
 * - FID_INPUT_ISCD         : 종목 코드 (6자리)
 *      예) 005930 = 삼성전자, 000660 = SK하이닉스
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisMarketClient {

    /**
     * 주식 현재가 시세 TR ID
     * FHKST01010100 = 실전투자용
     * (모의투자는 다른 TR ID 사용, 현재 미지원)
     */
    private static final String TR_ID_INQUIRE_PRICE = "FHKST01010100";

    private final WebClient kisWebClient;
    private final KisHeaderFactory headerFactory;

    /**
     * 종목 현재가 조회
     *
     * @param stockCode 종목 코드 6자리 (예: "005930")
     * @return KIS 현재가 응답
     */
    public KisCurrentPriceResponse getCurrentPrice(String stockCode) {
        log.debug("[KIS] 현재가 조회 요청: stockCode={}", stockCode);

        try {
            KisCurrentPriceResponse response = kisWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // 주식 시장
                            .queryParam("FID_INPUT_ISCD", stockCode)     // 종목 코드
                            .build())
                    .headers(headers -> headers.addAll(headerFactory.createHeaders(TR_ID_INQUIRE_PRICE)))
                    .retrieve()
                    .bodyToMono(KisCurrentPriceResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("KIS", "현재가 조회 응답 없음: " + stockCode);
            }
            if (!response.isSuccess()) {
                throw new ExternalApiException("KIS",
                        "현재가 조회 실패: stockCode=" + stockCode + ", msg=" + response.message());
            }

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "현재가 조회 중 오류: " + e.getMessage());
        }
    }
}
