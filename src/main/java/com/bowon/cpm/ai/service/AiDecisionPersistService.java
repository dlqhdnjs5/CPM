package com.bowon.cpm.ai.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.domain.AiDecisionFactor;
import com.bowon.cpm.ai.domain.AiDecisionRawResponse;
import com.bowon.cpm.ai.domain.AiPromptLog;
import com.bowon.cpm.ai.mapper.AiDecisionFactorMapper;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.ai.mapper.AiDecisionRawResponseMapper;
import com.bowon.cpm.ai.mapper.AiPromptLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 판단 저장 전용 서비스.
 *
 * 분리 이유:
 *  - {@link AiDecisionService#generateDecision}는 트랜잭션 외부에서 외부 API를 호출한다.
 *  - 같은 클래스 내 self-invocation은 Spring AOP 프록시를 거치지 않아
 *    {@code @Transactional(REQUIRES_NEW)}가 적용되지 않는다.
 *  - 별도 빈으로 분리해야 프록시가 적용되어 각 INSERT/UPDATE가
 *    독립 트랜잭션으로 즉시 커밋된다 (외부 API 실패해도 직전까지의 저장은 보존).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDecisionPersistService {

    private final AiPromptLogMapper promptLogMapper;
    private final AiDecisionRawResponseMapper rawResponseMapper;
    private final AiDecisionMapper decisionMapper;
    private final AiDecisionFactorMapper decisionFactorMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertPromptLog(AiPromptLog promptLog) {
        promptLogMapper.insert(promptLog);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertRawResponse(AiDecisionRawResponse raw) {
        rawResponseMapper.insert(raw);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateParseError(Long rawResponseId, String parseError) {
        try {
            rawResponseMapper.updateParseError(rawResponseId, parseError);
        } catch (Exception e) {
            log.warn("[AI] parse_error 저장 실패: id={}, error={}", rawResponseId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateParsedSuccess(Long rawResponseId) {
        try {
            rawResponseMapper.updateParsedSuccess(rawResponseId);
        } catch (Exception e) {
            log.warn("[AI] parsed_success 저장 실패: id={}, error={}", rawResponseId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertDecision(AiDecision decision) {
        decisionMapper.insert(decision);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDecisionStatus(Long decisionId, String status) {
        decisionMapper.updateDecisionStatus(decisionId, status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertFactors(List<AiDecisionFactor> factors) {
        decisionFactorMapper.insertBatch(factors);
    }
}

