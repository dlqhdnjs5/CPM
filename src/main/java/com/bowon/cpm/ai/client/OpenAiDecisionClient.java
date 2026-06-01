package com.bowon.cpm.ai.client;

import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 클라이언트
 *
 * API: POST /v1/responses
 * 인증: Authorization: Bearer {api-key}
 *
 * json_schema strict:true 사용으로 구조화된 JSON 응답 보장
 * AI가 반드시 지정한 스키마 형태로만 응답하도록 강제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiDecisionClient {

    private final WebClient openAiWebClient;
    private final OpenAiProperties properties;

    /**
     * AI 매매 판단 생성
     *
     * @param systemPrompt 시스템 프롬프트 (AI 역할 지시)
     * @param userPrompt   사용자 프롬프트 (종목 분석 데이터)
     * @return OpenAI 응답 (output[].content[].text 에 JSON 포함)
     */
    public OpenAiResponse createDecision(String systemPrompt, String userPrompt) {
        return createDecision(systemPrompt, userPrompt, properties.modelDecision());
    }

    /**
     * 모델을 직접 지정해서 AI 판단 생성 (재검토 등 용도)
     *
     * @param model 사용할 모델명 (예: gpt-4.1, gpt-4.1-mini)
     */
    public OpenAiResponse createDecision(String systemPrompt, String userPrompt, String model) {
        log.debug("[OpenAI] AI 판단 요청 시작. model={}", model);

        Map<String, Object> request = buildRequest(systemPrompt, userPrompt, model);

        try {
            OpenAiResponse response = openAiWebClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("OPENAI", "AI 응답 없음");
            }

            log.debug("[OpenAI] AI 응답 수신. status={}, tokens={}",
                    response.status(),
                    response.usage() != null ? response.usage().totalTokens() : "?");

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("OPENAI", "AI API 호출 중 오류: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRequest(String systemPrompt, String userPrompt, String model) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("input", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        // json_schema strict:true — AI가 반드시 아래 스키마 형태로만 응답
        request.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "stock_trade_decision",
                        "strict", true,
                        "schema", buildDecisionSchema()
                )
        ));
        return request;
    }

    /**
     * AI 매매 판단 JSON Schema 정의
     * strict:true 이므로 additionalProperties:false 필수
     * required에 모든 필드 포함 필수
     */
    private Map<String, Object> buildDecisionSchema() {
        // Map.of()는 최대 10쌍 제한 → Map.ofEntries() 사용
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("stockCode", Map.of("type", "string"));
        properties.put("stockName", Map.of("type", "string"));
        properties.put("decision", Map.of("type", "string", "enum", List.of("BUY", "SELL", "HOLD")));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        properties.put("currentPrice", Map.of("type", "number"));
        properties.put("targetPrice", Map.of("type", "number"));
        properties.put("stopLossPrice", Map.of("type", "number"));
        properties.put("expectedReturnRate", Map.of("type", "number"));
        properties.put("expectedLossRate", Map.of("type", "number"));
        properties.put("riskRewardRatio", Map.of("type", "number"));
        properties.put("recommendedPortfolioWeight", Map.of("type", "number", "minimum", 0, "maximum", 1));
        properties.put("expectedHoldingDays", Map.of("type", "integer"));
        properties.put("riskLevel", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH")));
        properties.put("reason", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(
                "stockCode", "stockName", "decision", "confidence",
                "currentPrice", "targetPrice", "stopLossPrice",
                "expectedReturnRate", "expectedLossRate", "riskRewardRatio",
                "recommendedPortfolioWeight", "expectedHoldingDays",
                "riskLevel", "reason"
        ));
        schema.put("properties", properties);
        return schema;
    }
}


