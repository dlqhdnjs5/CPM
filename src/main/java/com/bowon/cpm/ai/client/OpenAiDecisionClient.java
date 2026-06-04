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
     * 텍스트 응답 전용 (JSON Schema 없음)
     * 요약, 분석 등 자유 형식 텍스트 응답이 필요한 경우 사용
     */
    public OpenAiResponse createTextCompletion(String systemPrompt, String userPrompt, String model) {
        log.debug("[OpenAI] 텍스트 요청. model={}", model);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("input", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        // JSON Schema 없음 — 자유 형식 텍스트 응답

        try {
            OpenAiResponse response = openAiWebClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("OPENAI", "텍스트 응답 없음");
            }
            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("OPENAI", "텍스트 API 호출 오류: " + e.getMessage());
        }
    }

    /**
     * AI 매매 판단 JSON Schema 정의
     * strict:true 이므로 additionalProperties:false 필수
     * required에 모든 필드 포함 필수
     *
     * P3 확장: analysis 객체 + factors 배열 추가
     * - strict 모드에서 null 허용을 위해 type을 ["string", "null"] / ["number", "null"]로 union
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

        // === P3: analysis 객체 (각 sub-field는 nullable) ===
        properties.put("analysis", buildAnalysisSchema());
        // === P3: factors 배열 (빈 배열 허용) ===
        properties.put("factors", buildFactorsSchema());

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(
                "stockCode", "stockName", "decision", "confidence",
                "currentPrice", "targetPrice", "stopLossPrice",
                "expectedReturnRate", "expectedLossRate", "riskRewardRatio",
                "recommendedPortfolioWeight", "expectedHoldingDays",
                "riskLevel", "reason",
                "analysis", "factors"
        ));
        schema.put("properties", properties);
        return schema;
    }

    /**
     * analysis 객체 스키마.
     * 영역별 분석 텍스트. 데이터 부족 시 null 허용.
     */
    private Map<String, Object> buildAnalysisSchema() {
        // strict 모드에서 nullable 처리: type 을 ["string","null"] 로
        Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("technicalAnalysis", nullableString);
        props.put("newsAnalysis", nullableString);
        props.put("disclosureAnalysis", nullableString);
        props.put("fundamentalAnalysis", nullableString);
        props.put("supplyDemandAnalysis", nullableString);

        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("type", "object");
        obj.put("additionalProperties", false);
        obj.put("required", List.of(
                "technicalAnalysis", "newsAnalysis",
                "disclosureAnalysis", "fundamentalAnalysis",
                "supplyDemandAnalysis"
        ));
        obj.put("properties", props);
        return obj;
    }

    /**
     * factors 배열 스키마.
     * 각 factor는 type/direction/score/summary 4필드 모두 필수.
     */
    private Map<String, Object> buildFactorsSchema() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("type", Map.of(
                "type", "string",
                "enum", List.of("TECHNICAL", "NEWS", "DART", "FUNDAMENTAL", "SUPPLY_DEMAND")
        ));
        itemProps.put("direction", Map.of(
                "type", "string",
                "enum", List.of("POSITIVE", "NEGATIVE", "NEUTRAL")
        ));
        itemProps.put("score", Map.of("type", "number", "minimum", 0, "maximum", 1));
        itemProps.put("summary", Map.of("type", "string"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("additionalProperties", false);
        item.put("required", List.of("type", "direction", "score", "summary"));
        item.put("properties", itemProps);

        Map<String, Object> arr = new LinkedHashMap<>();
        arr.put("type", "array");
        arr.put("items", item);
        return arr;
    }
}


