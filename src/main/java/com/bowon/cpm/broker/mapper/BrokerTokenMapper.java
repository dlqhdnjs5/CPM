package com.bowon.cpm.broker.mapper;

import com.bowon.cpm.broker.domain.BrokerToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * broker_token 매퍼
 * UK: (broker_type, token_type) → upsert 패턴
 */
@Mapper
public interface BrokerTokenMapper {

    /** 조회: broker_type + token_type 기준 단건 */
    Optional<BrokerToken> findByBrokerAndType(
            @Param("brokerType") String brokerType,
            @Param("tokenType") String tokenType
    );

    /**
     * Upsert: 동일 (broker_type, token_type) 있으면 토큰/만료시각 갱신
     * INSERT ... ON DUPLICATE KEY UPDATE
     */
    int upsert(BrokerToken token);
}

