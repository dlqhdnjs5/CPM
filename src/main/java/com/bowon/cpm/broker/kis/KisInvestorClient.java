package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.kis.dto.KisInvestorResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisInvestorClient {

    private static final String TR_ID_INQUIRE_INVESTOR = "FHKST01010900";

    private final WebClient kisWebClient;
    private final KisHeaderFactory headerFactory;

    public KisInvestorResponse getInvestorFlow(String stockCode) {
        log.debug("[KIS] investor flow request: stockCode={}", stockCode);

        try {
            KisInvestorResponse response = kisWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-investor")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .build())
                    .headers(headers -> headers.addAll(headerFactory.createHeaders(TR_ID_INQUIRE_INVESTOR)))
                    .retrieve()
                    .bodyToMono(KisInvestorResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("KIS", "investor flow response is empty: " + stockCode);
            }
            if (!response.isSuccess()) {
                throw new ExternalApiException("KIS",
                        "investor flow failed: stockCode=" + stockCode + ", msg=" + response.message());
            }
            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("KIS", "investor flow error: " + e.getMessage());
        }
    }
}
