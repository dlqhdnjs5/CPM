package com.bowon.cpm.order.policy;

import org.springframework.stereotype.Component;

/**
 * 기존 호환용 BUY 주문 정책 엔진.
 * 신규 코드는 {@link BuyOrderPolicyEngine}을 직접 사용한다.
 */
@Deprecated
@Component
public class OrderPolicyEngine extends BuyOrderPolicyEngine {
}

