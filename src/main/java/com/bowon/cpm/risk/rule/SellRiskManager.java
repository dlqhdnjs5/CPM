package com.bowon.cpm.risk.rule;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import org.springframework.stereotype.Component;

@Component
public class SellRiskManager {

    public String check(
            AiDecision decision,
            RiskPolicyConfig policy,
            PortfolioPosition position,
            int requestedQuantity,
            SellTrigger trigger
    ) {
        if (position == null || position.getQuantity() == null || position.getQuantity() <= 0) {
            return "보유 수량 없음";
        }
        if (requestedQuantity < 1) {
            return "매도 수량 0";
        }
        if (requestedQuantity > position.getQuantity()) {
            return "보유 수량 초과 매도";
        }
        Integer availableQuantity = position.getAvailableQuantity();
        if (availableQuantity != null && requestedQuantity > availableQuantity) {
            return "매도 가능 수량 초과";
        }
        if (SellTrigger.AI_DECISION.equals(trigger)
                && "SELL".equals(decision.getDecision())
                && decision.getConfidence() != null
                && policy.getMinConfidence() != null
                && decision.getConfidence().compareTo(policy.getMinConfidence()) < 0) {
            return "AI 신뢰도 부족 (SELL)";
        }
        return null;
    }
}
