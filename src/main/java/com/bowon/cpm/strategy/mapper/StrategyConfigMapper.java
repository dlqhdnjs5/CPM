package com.bowon.cpm.strategy.mapper;

import com.bowon.cpm.strategy.domain.StrategyConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StrategyConfigMapper {
    List<StrategyConfig> findAll();

    Optional<StrategyConfig> findByStrategyCode(String strategyCode);

    int updateSelective(StrategyConfig config);
}
