package com.bowon.cpm.ai.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 테이블: ai_decision_raw_response */
@Getter @Builder
public class AiDecisionRawResponse {
    @Setter
    private Long id;
    private Long promptLogId;
    private String stockCode;
    private String modelName;
    private String responseText;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Boolean isParsed;
    private String parseError;
    private LocalDateTime createdAt;
}
