package com.bowon.cpm.order.mapper;

import com.bowon.cpm.order.domain.OrderRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderRequestMapper {
    /** INSERT — useGeneratedKeys로 id 반환 */
    void insert(OrderRequest orderRequest);

    /** idempotency_key로 중복 주문 확인 */
    Optional<OrderRequest> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderRequest> findById(Long id);

    /** broker_order_no + status 업데이트 */
    void updateBrokerOrderNo(
            @Param("id") Long id,
            @Param("brokerOrderNo") String brokerOrderNo,
            @Param("orderStatus") String orderStatus
    );

    /** status만 업데이트 */
    void updateStatus(
            @Param("id") Long id,
            @Param("orderStatus") String orderStatus
    );

    List<OrderRequest> findByAccountNo(
            @Param("accountNo") String accountNo,
            @Param("limit") int limit
    );

    /** broker_order_no로 주문 조회 (체결 동기화 시 사용) */
    Optional<OrderRequest> findByBrokerOrderNo(String brokerOrderNo);

    boolean existsSellByTrigger(
            @Param("aiDecisionId") Long aiDecisionId,
            @Param("trigger") String trigger
    );

    boolean existsTodaySellByTrigger(
            @Param("aiDecisionId") Long aiDecisionId,
            @Param("trigger") String trigger
    );
}

