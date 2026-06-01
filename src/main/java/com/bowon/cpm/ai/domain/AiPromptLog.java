package com.bowon.cpm.ai.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 테이블: ai_prompt_log */
@Getter @Builder
public class AiPromptLog {
    @Setter
    private Long id;
    private String stockCode;
    /** DECISION, NEWS_SUMMARY, FEEDBACK */
    private String promptType;
    private String modelName;
    private String systemPrompt;
    private String userPrompt;
    private LocalDateTime createdAt;
}
