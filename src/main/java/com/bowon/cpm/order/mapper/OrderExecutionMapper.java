package com.bowon.cpm.order.mapper;

import com.bowon.cpm.order.domain.OrderExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderExecutionMapper {
    void insert(OrderExecution execution);

    /** broker_order_no로 이미 저장된 체결인지 확인 (중복 저장 방지) */
    Optional<OrderExecution> findByBrokerOrderNo(String brokerOrderNo);

    List<OrderExecution> findByStockCode(
            @Param("stockCode") String stockCode,
            @Param("limit") int limit
    );
}

