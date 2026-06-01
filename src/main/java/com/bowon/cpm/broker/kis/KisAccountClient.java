package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.kis.dto.KisBalanceResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * KIS 계좌 잔고 조회 클라이언트
 *
 * API: GET /uapi/domestic-stock/v1/trading/inquire-balance
 * TR_ID: TTTC8434R (실전투자 — 주식잔고조회)
 *
 * Query Parameter 설명:
 * - CANO             : 계좌번호 앞 8~10자리 (accountNo에서 '-' 앞 부분)
 *                      예) "50123456"
 * - ACNT_PRDT_CD     : 계좌 상품 코드 (보통 "01" = 종합계좌)
 * - AFHR_FLPR_YN     : 시간외 단일가 여부 ("N" = 미적용)
 * - OFL_YN           : 오프라인 여부 (공백 = 미사용)
 * - INQR_DVSN        : 조회 구분
 *                      "01" = 대출일별  "02" = 종목별 (종목별 합산 조회 사용)
 * - UNPR_DVSN        : 단가 구분 ("01" = 기본 단가)
 * - FUND_STTL_ICLD_YN: 펀드 결제 포함 여부 ("N")
 * - FNCG_AMT_AUTO_RDPT_YN: 융자금액 자동상환 여부 ("N")
 * - PRCS_DVSN        : 처리 구분 ("01" = 전일매매포함)
 * - CTX_AREA_FK100   : 연속 조회 키 (첫 조회 시 공백)
 * - CTX_AREA_NK100   : 연속 조회 키 (첫 조회 시 공백)
 *
 * 응답:
 * - output1 : 보유 종목 목록
 * - output2 : 계좌 잔고 요약 (예수금, 총자산 등)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisAccountClient {

    /**
     * 주식잔고조회 TR ID
     * TTTC8434R = 실전투자용
     * (모의투자: VTTC8434R, 현재 미지원)
     */
    private static final String TR_ID_BALANCE = "TTTC8434R";

    private final WebClient kisWebClient;
    private final KisProperties properties;
    private final KisHeaderFactory headerFactory;

    /**
     * 계좌 잔고 및 보유 종목 조회
     *
     * @return KIS 잔고 응답 (output1: 보유종목, output2: 계좌잔고)
     */
    public KisBalanceResponse getBalance() {
        log.debug("[KIS] 계좌 잔고 조회 요청");

        try {
            KisBalanceResponse response = kisWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/trading/inquire-balance")
                            .queryParam("CANO", extractCano())                    // 계좌번호 앞자리
                            .queryParam("ACNT_PRDT_CD", properties.accountProductCode()) // 상품코드 "01"
                            .queryParam("AFHR_FLPR_YN", "N")   // 시간외 단일가 미적용
                            .queryParam("OFL_YN", "")           // 오프라인 미사용
                            .queryParam("INQR_DVSN", "02")      // 종목별 합산 조회
                            .queryParam("UNPR_DVSN", "01")      // 기본 단가
                            .queryParam("FUND_STTL_ICLD_YN", "N")
                            .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                            .queryParam("PRCS_DVSN", "01")      // 전일매매 포함
                            .queryParam("CTX_AREA_FK100", "")   // 연속조회 키 (첫 요청은 공백)
                            .queryParam("CTX_AREA_NK100", "")   // 연속조회 키 (첫 요청은 공백)
                            .build())
                    .headers(headers -> headers.addAll(headerFactory.createHeaders(TR_ID_BALANCE)))
                    .retrieve()
                    .bodyToMono(KisBalanceResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("KIS", "계좌 잔고 조회 응답 없음");
            }
            if (!response.isSuccess()) {
                throw new ExternalApiException("KIS", "계좌 잔고 조회 실패: msg=" + response.message());
            }

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "계좌 잔고 조회 중 오류: " + e.getMessage());
        }
    }

    /**
     * 계좌번호에서 CANO(앞 8~10자리) 추출
     * KIS API는 계좌번호를 CANO(앞자리)와 ACNT_PRDT_CD(뒷자리)로 분리해서 전달한다.
     *
     * 예) accountNo = "50123456-01"  →  CANO = "50123456", ACNT_PRDT_CD = "01"
     */
    private String extractCano() {
        String accountNo = properties.accountNo();
        if (accountNo == null || accountNo.isBlank()) {
            throw new ExternalApiException("KIS", "계좌번호가 설정되지 않았습니다. application-local.yml을 확인하세요.");
        }
        return accountNo.contains("-") ? accountNo.split("-")[0] : accountNo;
    }
}
