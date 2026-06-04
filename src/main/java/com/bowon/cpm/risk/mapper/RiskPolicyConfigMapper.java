package com.bowon.cpm.risk.mapper;

import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RiskPolicyConfigMapper {
    /** 활성화된 정책 코드로 조회 */
    Optional<RiskPolicyConfig> findByPolicyCode(String policyCode);

    List<RiskPolicyConfig> findAllActive();

    int updateSelective(RiskPolicyConfig policy);
}

