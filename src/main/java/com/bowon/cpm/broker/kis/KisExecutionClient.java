package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.kis.dto.KisExecutionResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * KIS 당일 체결 내역 조회 클라이언트
 *
 * API: GET /uapi/domestic-stock/v1/trading/inquire-daily-ccld
 * TR ID: TTTC8001R (실전 주식 당일 체결 조회)
 *
 * 주요 파라미터:
 *   CANO           - 계좌번호 앞 8자리
 *   ACNT_PRDT_CD   - 계좌 상품 코드 (01)
 *   INQR_STRT_DT   - 조회 시작일 (yyyyMMdd)
 *   INQR_END_DT    - 조회 종료일 (yyyyMMdd)
 *   SLL_BUY_DVSN_CD - 매도매수구분 (00=전체, 01=매도, 02=매수)
 *   INQR_DVSN      - 조회구분 (00=역순)
 *   PDNO           - 종목코드 (공백=전체)
 *   CCLD_DVSN      - 체결구분 (00=전체, 01=체결, 02=미체결)
 *   ORD_GNO_BRNO   - 주문채번지점번호 (공백)
 *   ODNO           - 주문번호 (공백=전체)
 *   INQR_DVSN_3    - 조회구분3 (00)
 *   INQR_DVSN_1    - 조회구분1 (공백)
 *   CTX_AREA_FK100 - 연속조회키 (공백)
 *   CTX_AREA_NK100 - 연속조회키 (공백)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisExecutionClient {

    private static final String TR_ID = "TTTC8001R";
    private static final String PATH = "/uapi/domestic-stock/v1/trading/inquire-daily-ccld";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient kisWebClient;
    private final KisProperties properties;
    private final KisHeaderFactory headerFactory;

    /**
     * 당일 체결 내역 조회 (전체 종목)
     *
     * @param date 조회 날짜
     */
    public KisExecutionResponse getExecutions(LocalDate date) {
        String dateStr = date.format(DATE_FMT);
        String accountPrefix = extractAccountPrefix();

        log.debug("[KIS] 체결 조회: date={}, accountNo={}", dateStr, accountPrefix);

        try {
            KisExecutionResponse response = kisWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PATH)
                            .queryParam("CANO", accountPrefix)
                            .queryParam("ACNT_PRDT_CD", properties.accountProductCode())
                            .queryParam("INQR_STRT_DT", dateStr)
                            .queryParam("INQR_END_DT", dateStr)
                            .queryParam("SLL_BUY_DVSN_CD", "00")   // 전체
                            .queryParam("INQR_DVSN", "00")           // 역순
                            .queryParam("PDNO", "")                  // 전체 종목
                            .queryParam("CCLD_DVSN", "01")           // 체결분만
                            .queryParam("ORD_GNO_BRNO", "")
                            .queryParam("ODNO", "")                  // 전체 주문번호
                            .queryParam("INQR_DVSN_3", "00")
                            .queryParam("INQR_DVSN_1", "")
                            .queryParam("CTX_AREA_FK100", "")
                            .queryParam("CTX_AREA_NK100", "")
                            .build())
                    .headers(h -> h.addAll(headerFactory.createHeaders(TR_ID)))
                    .retrieve()
                    .bodyToMono(KisExecutionResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("KIS", "체결 조회 응답 없음");
            }
            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "체결 조회 오류: " + e.getMessage());
        }
    }

    private String extractAccountPrefix() {
        String accountNo = properties.accountNo();
        if (accountNo == null || accountNo.isBlank()) return "";
        if (accountNo.contains("-")) return accountNo.split("-")[0];
        return accountNo.length() >= 8 ? accountNo.substring(0, 8) : accountNo;
    }
}

