package com.bowon.cpm.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI Responses API 응답 DTO
 * API: POST /v1/responses
 *
 * 응답 구조:
 * {
 *   "id": "resp_...",
 *   "status": "completed",
 *   "output": [
 *     {
 *       "type": "message",
 *       "content": [
 *         { "type": "output_text", "text": "{...json...}" }
 *       ]
 *     }
 *   ],
 *   "usage": { "input_tokens": 100, "output_tokens": 200 }
 * }
 */
public record OpenAiResponse(
        String id,
        String status,
        List<OutputItem> output,
        Usage usage
) {
    public record OutputItem(
            String type,
            String id,
            String status,
            String role,
            List<ContentItem> content
    ) {
    }

    public record ContentItem(
            String type,
            String text
    ) {
    }

    public record Usage(
            @JsonProperty("input_tokens")
            Integer inputTokens,

            @JsonProperty("output_tokens")
            Integer outputTokens,

            @JsonProperty("total_tokens")
            Integer totalTokens
    ) {
    }

    /**
     * AI 응답 JSON 텍스트 추출
     * output[0].content[0].text 에서 JSON 문자열 반환
     */
    public String extractText() {
        if (output == null || output.isEmpty()) return null;
        return output.stream()
                .filter(item -> item.content() != null)
                .flatMap(item -> item.content().stream())
                .filter(c -> c.text() != null && !c.text().isBlank())
                .map(ContentItem::text)
                .findFirst()
                .orElse(null);
    }
}

