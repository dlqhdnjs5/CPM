package com.bowon.cpm.dart.client;

import com.bowon.cpm.common.exception.ExternalApiException;
import com.bowon.cpm.dart.client.dto.DartDisclosureResponse;
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
public class DartDisclosureClient {

    private final DartProperties dartProperties;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public DartDisclosureResponse getDisclosureList(
            String corpCode, String beginDate, String endDate) {

        log.debug("[DART] disclosure list: corpCode={}, {} ~ {}", corpCode, beginDate, endDate);

        try {
            HttpRequest request = HttpRequest.newBuilder(disclosureListUri(corpCode, beginDate, endDate))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> httpResponse = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new ExternalApiException("DART",
                        "Disclosure list HTTP failed: status=" + httpResponse.statusCode());
            }

            DartDisclosureResponse response = objectMapper.readValue(
                    httpResponse.body(), DartDisclosureResponse.class);
            if (response == null) {
                throw new ExternalApiException("DART", "Disclosure list response empty: corpCode=" + corpCode);
            }

            if ("100".equals(response.status())) {
                log.debug("[DART] no disclosures: corpCode={}", corpCode);
                return response;
            }

            if (!response.isSuccess()) {
                throw new ExternalApiException("DART",
                        "Disclosure list failed: corpCode=" + corpCode
                                + ", status=" + response.status()
                                + ", msg=" + response.message());
            }

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("DART", "Disclosure list request failed: " + e.getMessage());
        }
    }

    private URI disclosureListUri(String corpCode, String beginDate, String endDate) {
        String baseUrl = normalizedBaseUrl();
        return URI.create(baseUrl + "/api/list.json"
                + "?crtfc_key=" + encode(dartProperties.apiKey())
                + "&corp_code=" + encode(corpCode)
                + "&bgn_de=" + encode(beginDate)
                + "&end_de=" + encode(endDate)
                + "&last_reprt_at=N"
                + "&page_count=100");
    }

    private String normalizedBaseUrl() {
        String baseUrl = dartProperties.baseUrl();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
