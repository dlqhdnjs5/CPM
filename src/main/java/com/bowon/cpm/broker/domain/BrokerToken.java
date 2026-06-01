package com.bowon.cpm.broker.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * broker_token 테이블 도메인
 * 증권사 OAuth 토큰 (서버 재시작/재배포 후에도 재사용)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerToken {
    private Long id;
    private String brokerType;   // KIS
    private String tokenType;    // ACCESS / APPROVAL_KEY
    private String accessToken;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

