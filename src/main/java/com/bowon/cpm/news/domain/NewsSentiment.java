package com.bowon.cpm.news.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class NewsSentiment {
    @Setter
    private Long id;
    private Long newsId;
    private String stockCode;
    private String sentiment;
    private BigDecimal sentimentScore;
    private BigDecimal impactScore;
    private String reason;
    private LocalDateTime createdAt;
}
