package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.kis.dto.KisOrderRequest;
import com.bowon.cpm.broker.kis.dto.KisOrderResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KIS 주문 API 클라이언트
 *
 * 매수/매도 주문 API: POST /uapi/domestic-stock/v1/trading/order-cash
 *
 * TR ID 설명:
 *   TTTC0802U = 주식 현금 매수 주문 (실전)
 *   TTTC0801U = 주식 현금 매도 주문 (실전)
 *
 * 주문 구분 코드 (ORD_DVSN):
 *   "00" = 지정가
 *   "01" = 시장가
 *
 * 주의:
 * - 주문 전 반드시 order_request를 DB에 먼저 저장한다.
 * - 타임아웃 발생 시 즉시 재주문하지 않는다. 미체결 조회로 접수 여부를 먼저 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisOrderClient {

    /** 실전 매수 TR ID */
    private static final String TR_ID_BUY = "TTTC0802U";
    /** 실전 매도 TR ID */
    private static final String TR_ID_SELL = "TTTC0801U";
    /** 주문 API 경로 */
    private static final String ORDER_PATH = "/uapi/domestic-stock/v1/trading/order-cash";

    private final WebClient kisWebClient;
    private final KisProperties properties;
    private final KisHeaderFactory headerFactory;

    /**
     * 매수 주문
     *
     * @param request 주문 요청 (종목코드, 수량, 가격, 주문구분)
     */
    public KisOrderResponse placeBuyOrder(KisOrderRequest request) {
        log.info("[KIS] 매수 주문: stockCode={}, qty={}, price={}, type={}",
                request.stockCode(), request.quantity(), request.orderPrice(), request.orderDivisionCode());
        return placeOrder(TR_ID_BUY, request);
    }

    /**
     * 매도 주문
     *
     * @param request 주문 요청 (종목코드, 수량, 가격, 주문구분)
     */
    public KisOrderResponse placeSellOrder(KisOrderRequest request) {
        log.info("[KIS] 매도 주문: stockCode={}, qty={}, price={}, type={}",
                request.stockCode(), request.quantity(), request.orderPrice(), request.orderDivisionCode());
        return placeOrder(TR_ID_SELL, request);
    }

    private KisOrderResponse placeOrder(String trId, KisOrderRequest request) {
        try {
            // 요청 바디 구성
            // CANO: 계좌번호 앞 8자리
            // ACNT_PRDT_CD: 계좌 상품 코드 (01)
            // PDNO: 종목 코드
            // ORD_DVSN: 주문 구분 (00=지정가, 01=시장가)
            // ORD_QTY: 주문 수량 (문자열)
            // ORD_UNPR: 주문 단가 (시장가는 "0")
            Map<String, String> body = new LinkedHashMap<>();
            body.put("CANO", extractAccountPrefix());
            body.put("ACNT_PRDT_CD", properties.accountProductCode());
            body.put("PDNO", request.stockCode());
            body.put("ORD_DVSN", request.orderDivisionCode());
            body.put("ORD_QTY", String.valueOf(request.quantity()));
            body.put("ORD_UNPR", request.orderPrice());

            KisOrderResponse response = kisWebClient.post()
                    .uri(ORDER_PATH)
                    .headers(headers -> headers.addAll(headerFactory.createHeaders(trId)))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(KisOrderResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("KIS", "주문 응답 없음");
            }

            // rt_cd가 "0"이 아니면 KIS 측 오류
            if (!"0".equals(response.resultCode())) {
                throw new ExternalApiException("KIS",
                        "주문 실패: rt_cd=" + response.resultCode() + ", msg=" + response.message());
            }

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "주문 API 호출 오류: " + e.getMessage());
        }
    }

    /**
     * 계좌번호에서 앞 8자리 추출
     * KIS API는 CANO(8자리)와 ACNT_PRDT_CD(2자리)를 분리해서 전송
     */
    private String extractAccountPrefix() {
        String accountNo = properties.accountNo();
        if (accountNo == null || accountNo.isBlank()) return "찾아서 넣으세요";
        // "12345678-01" 형태이면 "-" 앞만 사용, 아니면 앞 8자리
        if (accountNo.contains("-")) {
            return accountNo.split("-")[0];
        }
        return accountNo.length() >= 8 ? accountNo.substring(0, 8) : accountNo;
    }
}

