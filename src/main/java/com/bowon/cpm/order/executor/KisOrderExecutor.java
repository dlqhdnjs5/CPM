package com.bowon.cpm.order.executor;

import com.bowon.cpm.broker.kis.KisOrderClient;
import com.bowon.cpm.broker.kis.dto.KisOrderRequest;
import com.bowon.cpm.broker.kis.dto.KisOrderResponse;
import com.bowon.cpm.order.domain.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KisOrderExecutor {

    private final KisOrderClient kisOrderClient;

    public String execute(OrderRequest orderRequest) {
        String orderDivisionCode = "MARKET".equals(orderRequest.getOrderType()) ? "01" : "00";
        String orderPrice = "MARKET".equals(orderRequest.getOrderType())
                ? "0"
                : orderRequest.getOrderPrice().toPlainString();

        KisOrderRequest kisRequest = new KisOrderRequest(
                orderRequest.getStockCode(),
                orderRequest.getOrderQuantity(),
                orderPrice,
                orderDivisionCode
        );

        KisOrderResponse response;
        if ("BUY".equals(orderRequest.getOrderSide())) {
            response = kisOrderClient.placeBuyOrder(kisRequest);
        } else {
            response = kisOrderClient.placeSellOrder(kisRequest);
        }
        return response.getBrokerOrderNo();
    }
}
