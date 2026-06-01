package com.bowon.cpm.order.mapper;

import com.bowon.cpm.order.domain.OrderStatusHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderStatusHistoryMapper {
    void insert(OrderStatusHistory history);
}

