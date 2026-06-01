package com.bowon.cpm.common.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BrokerApiLog {
    private Long id;
    private String brokerType;
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

