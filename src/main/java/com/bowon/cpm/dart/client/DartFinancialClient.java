package com.bowon.cpm.dart.client;

import com.bowon.cpm.common.exception.ExternalApiException;
import com.bowon.cpm.dart.client.dto.DartFinancialResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class DartFinancialClient {

    private final DartProperties dartProperties;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public DartFinancialResponse getFinancialStatement(
            String corpCode, int businessYear, String reportCode) {

        log.debug("[DART] financial statement: corpCode={}, year={}, report={}",
                corpCode, businessYear, reportCode);

        try {
            HttpRequest request = HttpRequest.newBuilder(financialStatementUri(corpCode, businessYear, reportCode))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> httpResponse = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new ExternalApiException("DART",
                        "Financial statement HTTP failed: status=" + httpResponse.statusCode());
            }

            DartFinancialResponse response = objectMapper.readValue(
                    httpResponse.body(), DartFinancialResponse.class);
            if (response == null) {
                throw new ExternalApiException("DART", "Financial statement response empty: corpCode=" + corpCode);
            }
            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("DART", "Financial statement request failed: " + e.getMessage());
        }
    }

    private URI financialStatementUri(String corpCode, int businessYear, String reportCode) {
        String baseUrl = normalizedBaseUrl();
        return URI.create(baseUrl + "/api/fnlttSinglAcnt.json"
                + "?crtfc_key=" + encode(dartProperties.apiKey())
                + "&corp_code=" + encode(corpCode)
                + "&bsns_year=" + businessYear
                + "&reprt_code=" + encode(reportCode));
    }

    private String normalizedBaseUrl() {
        String baseUrl = dartProperties.baseUrl();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
