package com.bowon.cpm.feedback.mapper;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.support.fixture.AiDecisionFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 17 Case 11, 12, 13-b: AiFeedbackMapper 테스트.
 * 운영 DB 사용 + @MybatisTest 기본 @Transactional → 메서드 종료 시 자동 롤백.
 */
@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiFeedbackMapperTest {

    @Autowired AiFeedbackMapper feedbackMapper;
    @Autowired AiDecisionMapper decisionMapper;

    /** ai_feedback FK 충족용으로 ai_decision 1건 insert. */
    private Long insertDecision(String stockCode) {
        AiDecision d = AiDecisionFixture.defaults()
                .id(null)
                .stockCode(stockCode)
                .build();
        decisionMapper.insert(d);
        return d.getId();
    }

    private AiFeedback feedback(Long decisionId, String stockCode, String type,
                                Boolean success, Boolean target, Boolean stopLoss,
                                BigDecimal returnRate) {
        return AiFeedback.builder()
                .aiDecisionId(decisionId)
                .stockCode(stockCode)
                .evaluationType(type)
                .basePrice(new BigDecimal("10000"))
                .evaluatedPrice(new BigDecimal("10500"))
                .returnRate(returnRate)
                .targetReached(target)
                .stopLossReached(stopLoss)
                .success(success)
                .feedbackSummary("test")
                .build();
    }

    @Test
    @DisplayName("Case 11: findRecentByStockCodeAndTypes — DAILY 제외")
    void findRecentByStockCodeAndTypes_excludesDaily() {
        Long d1 = insertDecision("TEST_011");
        Long d2 = insertDecision("TEST_011");
        Long d3 = insertDecision("TEST_011");
        Long d4 = insertDecision("TEST_011");
        Long d5 = insertDecision("TEST_011");

        feedbackMapper.insertIgnore(feedback(d1, "TEST_011", "DAILY", true, true, false, BigDecimal.ONE));
        feedbackMapper.insertIgnore(feedback(d2, "TEST_011", "DAILY", true, true, false, BigDecimal.ONE));
        feedbackMapper.insertIgnore(feedback(d3, "TEST_011", "WEEKLY", true, true, false, BigDecimal.ONE));
        feedbackMapper.insertIgnore(feedback(d4, "TEST_011", "MONTHLY", true, true, false, BigDecimal.ONE));
        feedbackMapper.insertIgnore(feedback(d5, "TEST_011", "HOLDING_END", true, true, false, BigDecimal.ONE));

        List<AiFeedback> result = feedbackMapper.findRecentByStockCodeAndTypes(
                "TEST_011", List.of("WEEKLY", "MONTHLY", "HOLDING_END"), 10);

        assertThat(result).hasSize(3);
        assertThat(result).noneMatch(f -> "DAILY".equals(f.getEvaluationType()));
    }

    @Test
    @DisplayName("Case 12: aggregateStatsBetween — HOLDING_END 평균/MAX/MIN/카운트")
    void aggregateStatsBetween_correctness() {
        // HOLDING_END 5건: return 5, -2, 8, 1, -4 / success T,F,T,T,F
        for (Object[] row : new Object[][]{
                {new BigDecimal("5.0"), true, true, false},
                {new BigDecimal("-2.0"), false, false, true},
                {new BigDecimal("8.0"), true, true, false},
                {new BigDecimal("1.0"), true, false, false},
                {new BigDecimal("-4.0"), false, false, true},
        }) {
            Long dId = insertDecision("TEST_012");
            feedbackMapper.insertIgnore(feedback(dId, "TEST_012", "HOLDING_END",
                    (Boolean) row[1], (Boolean) row[2], (Boolean) row[3], (BigDecimal) row[0]));
        }

        LocalDate today = LocalDate.now();
        Map<String, Object> stats = feedbackMapper.aggregateStatsBetween(today.minusDays(1), today);

        assertThat(stats).isNotNull();
        assertThat(((Number) stats.get("total")).intValue()).isEqualTo(5);
        assertThat(((Number) stats.get("success_cnt")).intValue()).isEqualTo(3);
        assertThat(((Number) stats.get("target_cnt")).intValue()).isEqualTo(2);
        assertThat(((Number) stats.get("stop_loss_cnt")).intValue()).isEqualTo(2);
        assertThat(new BigDecimal(stats.get("avg_return").toString())).isEqualByComparingTo(new BigDecimal("1.6"));
        assertThat(new BigDecimal(stats.get("max_return").toString())).isEqualByComparingTo(new BigDecimal("8.0"));
        assertThat(new BigDecimal(stats.get("min_return").toString())).isEqualByComparingTo(new BigDecimal("-4.0"));
    }

    @Test
    @DisplayName("Case 13-b: insertIgnore 중복 호출 시 UK 차단 → 행 수 1")
    void insertIgnore_uk_blocks_duplicate() {
        Long dId = insertDecision("TEST_013");

        AiFeedback f1 = feedback(dId, "TEST_013", "HOLDING_END", true, true, false, new BigDecimal("5.0"));
        AiFeedback f2 = feedback(dId, "TEST_013", "HOLDING_END", false, false, true, new BigDecimal("-5.0"));

        feedbackMapper.insertIgnore(f1);
        feedbackMapper.insertIgnore(f2); // 동일 (decisionId + type) → UK 차단

        List<AiFeedback> all = feedbackMapper.findByAiDecisionId(dId);
        assertThat(all).hasSize(1);
        // 첫번째 행이 살아남음
        assertThat(all.get(0).getSuccess()).isTrue();
    }
}

