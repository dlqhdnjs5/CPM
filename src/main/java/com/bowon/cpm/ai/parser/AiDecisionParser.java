package com.bowon.cpm.ai.parser;

import com.bowon.cpm.ai.domain.AiTradeDecisionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI 응답 JSON 텍스트 → AiTradeDecisionJson 파싱
 * 파싱 실패 시 IllegalStateException 발생 → 호출부에서 catch하여 raw_response에 parse_error 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiDecisionParser {

    private final ObjectMapper objectMapper;

    public AiTradeDecisionJson parse(String responseText) {
        try {
            return objectMapper.readValue(responseText, AiTradeDecisionJson.class);
        } catch (Exception e) {
            log.error("[AI] JSON 파싱 실패: {}", e.getMessage());
            throw new IllegalStateException("AI 판단 JSON 파싱 실패: " + e.getMessage(), e);
        }
    }
}

