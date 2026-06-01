package com.bowon.cpm.risk.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.portfolio.service.PortfolioService;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import com.bowon.cpm.risk.mapper.RiskPolicyConfigMapper;
import com.bowon.cpm.risk.rule.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private static final String DEFAULT_POLICY_CODE = "DEFAULT_RISK_POLICY";

    private final RiskManager riskManager;
    private final RiskPolicyConfigMapper riskPolicyConfigMapper;
    private final RiskCheckResultMapper riskCheckResultMapper;
    private final AiDecisionMapper aiDecisionMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final KisProperties kisProperties;
    /**
     * syncAccountBalance() 1번 호출로:
     * - KIS API로 실시간 잔고 조회
     * - account_balance DB 저장 (이력 보존)
     * - portfolio_position DB 저장
     * 가 모두 처리됨 → 별도로 brokerClient.getAccountBalance()를 추가 호출하지 않음
     */
    private final PortfolioService portfolioService;

    /**
     * AI 판단에 대한 리스크 검증 수행 + risk_check_result 저장
     *
     * @param aiDecisionId AI 판단 ID
     * @return 검증 결과 (passed=true/false)
     */
    @Transactional
    public RiskCheckResult checkAndSave(Long aiDecisionId) {
        // 1. AI 판단 조회
        AiDecision decision = aiDecisionMapper.findById(aiDecisionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 판단 없음: id=" + aiDecisionId));

        // 2. 리스크 정책 조회
        RiskPolicyConfig policy = riskPolicyConfigMapper.findByPolicyCode(DEFAULT_POLICY_CODE)
                .orElseThrow(() -> new IllegalStateException("리스크 정책 없음: " + DEFAULT_POLICY_CODE));

        // 3. KIS API 1번 호출 → 잔고 조회 + DB 저장 동시 처리 (REQUIRES_NEW 별도 트랜잭션)
        String accountNo = kisProperties.accountNo();
        BigDecimal availableCash = BigDecimal.ZERO;
        BigDecimal totalAsset = BigDecimal.ZERO;

        try {
            AccountBalanceResult balanceResult = portfolioService.syncAccountBalance();
            availableCash = balanceResult.getAvailableCash() != null
                    ? balanceResult.getAvailableCash() : BigDecimal.ZERO;
            totalAsset = balanceResult.getTotalAssetAmount() != null
                    ? balanceResult.getTotalAssetAmount() : BigDecimal.ZERO;
            log.info("[Risk] 실시간 잔고 조회 완료: availableCash={}, totalAsset={}", availableCash, totalAsset);
        } catch (Exception e) {
            // 잔고 조회 실패 시 예수금=0으로 처리 → 리스크 검증에서 차단됨
            log.warn("[Risk] KIS 잔고 조회 실패 (예수금=0으로 처리): {}", e.getMessage());
        }

        // 4. 해당 종목 현재 보유 평가금액 조회
        BigDecimal currentPositionAmount = BigDecimal.ZERO;
        Optional<PortfolioPosition> posOpt = portfolioPositionMapper
                .findByAccountNoAndStockCode(accountNo, decision.getStockCode());
        if (posOpt.isPresent() && posOpt.get().getValuationAmount() != null) {
            currentPositionAmount = posOpt.get().getValuationAmount();
        }

        // 5. 주문 예상 금액 계산
        BigDecimal expectedOrderAmount = BigDecimal.ZERO;
        if (decision.getRecommendedPortfolioWeight() != null && totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            expectedOrderAmount = totalAsset.multiply(decision.getRecommendedPortfolioWeight());
        }

        // 6. 리스크 검증
        String failReason = riskManager.check(decision, policy, availableCash, totalAsset, currentPositionAmount);
        boolean passed = (failReason == null);

        log.info("[Risk] 검증 결과: aiDecisionId={}, stockCode={}, decision={}, passed={}, failReason={}",
                aiDecisionId, decision.getStockCode(), decision.getDecision(), passed, failReason);

        // 7. risk_check_result 저장
        RiskCheckResult result = RiskCheckResult.builder()
                .aiDecisionId(aiDecisionId)
                .policyCode(DEFAULT_POLICY_CODE)
                .accountNo(accountNo)
                .stockCode(decision.getStockCode())
                .passed(passed)
                .failReason(failReason)
                .availableCash(availableCash)
                .expectedOrderAmount(expectedOrderAmount)
                .maxPositionAmount(totalAsset.compareTo(BigDecimal.ZERO) > 0
                        ? totalAsset.multiply(policy.getMaxPositionWeight()) : null)
                .currentPositionAmount(currentPositionAmount)
                .confidence(decision.getConfidence())
                .riskRewardRatio(decision.getRiskRewardRatio())
                .build();
        riskCheckResultMapper.insert(result);

        return result;
    }
}
