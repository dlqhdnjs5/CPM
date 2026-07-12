package com.bowon.cpm.news.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NewsAiSummary {
    @Setter
    private Long id;
    private Long newsId;
    private String stockCode;
    private String summary;
    private String keyPoints;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createdAt;
}
