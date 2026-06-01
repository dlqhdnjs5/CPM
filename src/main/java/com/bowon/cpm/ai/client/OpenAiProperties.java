package com.bowon.cpm.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.openai")
public record OpenAiProperties(
        String baseUrl,
        String apiKey,
        /** 종목 매매 판단 모델 (기본: gpt-4.1-mini) */
        String modelDecision,
        /** 뉴스/공시 요약 모델 (기본: gpt-4.1-mini) */
        String modelSummary,
        /** BUY confidence≥0.8 고신뢰 후보 재검토 모델 (기본: gpt-4.1) */
        String modelReview
) {
}
