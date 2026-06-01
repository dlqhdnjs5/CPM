package com.bowon.cpm.common.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 외부 API 호출 로그 (KIS 제외)
 * 테이블: external_api_call_log
 * provider: DART, NAVER, OPENAI
 */
@Getter
@Builder
public class ExternalApiCallLog {
    private Long id;
    /** API 제공자: DART, NAVER, OPENAI */
    private String provider;
    private String apiName;
    private String httpMethod;
    private String requestUrl;
    private String requestBody;
    private Integer responseStatus;
    private String responseBody;
    private Boolean success;
    private String errorMessage;
    private Long elapsedMs;
    private LocalDateTime calledAt;
}

