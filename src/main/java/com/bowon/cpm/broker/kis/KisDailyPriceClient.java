package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.kis.dto.KisDailyPriceResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * KIS 주식 일자별 시세 조회 클라이언트
 *
 * API: GET /uapi/domestic-stock/v1/quotations/inquire-daily-price
 * TR_ID: FHKST01010400 (실전투자 — 주식현재가 일자별)
 *
 * Query Parameter 설명:
 * - FID_COND_MRKT_DIV_CODE : 시장 구분 ("J" = 주식)
 * - FID_INPUT_ISCD         : 종목 코드 6자리
 * - FID_PERIOD_DIV_CODE    : 기간 구분
 *      "D" = 일봉
 *      "W" = 주봉
 *      "M" = 월봉
 * - FID_ORG_ADJ_PRC        : 수정주가 원주가 가격 여부
 *      "0" = 수정주가 반영
 *      "1" = 수정주가 미반영 (원주가)
 *
 * 응답: output2 — 최근 30영업일 기준 일자별 OHLCV
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisDailyPriceClient {

    /**
     * 주식현재가 일자별 TR ID
     * FHKST01010400 = 실전투자용
     */
    private static final String TR_ID_DAILY_PRICE = "FHKST01010400";

    private final WebClient kisWebClient;
    private final KisHeaderFactory headerFactory;

    /**
     * 종목 일봉 조회 (최근 30영업일)
     *
     * @param stockCode    종목 코드 6자리 (예: "005930")
     * @param periodDivCode 기간 구분 ("D"=일, "W"=주, "M"=월)
     * @return KIS 일봉 응답
     */
    public KisDailyPriceResponse getDailyPrice(String stockCode, String periodDivCode) {
        log.debug("[KIS] 일봉 조회 요청: stockCode={}, period={}", stockCode, periodDivCode);

        try {
            KisDailyPriceResponse response = kisWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")    // 주식 시장
                            .queryParam("FID_INPUT_ISCD", stockCode)       // 종목 코드
                            .queryParam("FID_PERIOD_DIV_CODE", periodDivCode) // 기간 구분
                            .queryParam("FID_ORG_ADJ_PRC", "0")            // 수정주가 반영
                            .build())
                    .headers(headers -> headers.addAll(headerFactory.createHeaders(TR_ID_DAILY_PRICE)))
                    .retrieve()
                    .bodyToMono(KisDailyPriceResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("KIS", "일봉 조회 응답 없음: " + stockCode);
            }

            // 실제 KIS 응답 내용 로그 (필드명 불일치 디버깅용)
            log.debug("[KIS] 일봉 응답: rt_cd={}, msg={}, dataSize={}",
                    response.resultCode(), response.message(),
                    response.output2() == null ? "null" : response.output2().size());

            if (!response.isSuccess()) {
                throw new ExternalApiException("KIS",
                        "일봉 조회 실패: stockCode=" + stockCode + ", msg=" + response.message());
            }

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "일봉 조회 중 오류: " + e.getMessage());
        }
    }
}


