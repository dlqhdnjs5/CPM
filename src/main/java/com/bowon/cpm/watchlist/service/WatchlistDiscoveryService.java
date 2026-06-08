package com.bowon.cpm.watchlist.service;

import com.bowon.cpm.admin.service.StockDataBootstrapService;
import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.WatchlistProperties;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import com.bowon.cpm.stock.service.StockService;
import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.domain.StockCandidateScore;
import com.bowon.cpm.watchlist.mapper.StockCandidateScoreMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistDiscoveryService {

    private static final int DEFAULT_NEWS_DISPLAY = 30;

    private final WatchlistProperties properties;
    private final KisProperties kisProperties;
    private final StockCandidateScoreMapper candidateScoreMapper;
    private final StockMasterMapper stockMasterMapper;
    private final StockService stockService;
    private final StockDataBootstrapService stockDataBootstrapService;
    private final CandidatePrefetchService candidatePrefetchService;
    private final WatchlistScoringEngine scoringEngine;
    private final OpenAiDecisionClient openAiDecisionClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public DiscoveryResult discover(boolean bootstrap, int newsAnalyzeLimit) {
        LocalDate scoredDate = LocalDate.now();
        String accountNo = kisProperties.accountNo();

        stockMasterMapper.markHeldPositionsWatched(accountNo);
        enforceMaxWatched(accountNo);

        int watchedBefore = stockMasterMapper.countWatched();
        int slots = Math.max(0, properties.maxWatchedStocks() - watchedBefore);
        int addLimit = Math.min(properties.maxDailyAdditions(), slots);
        CandidatePrefetchService.PrefetchBatchResult prefetchResult = addLimit > 0
                ? candidatePrefetchService.prefetch(properties.prefetchCandidateLimit(), newsAnalyzeLimit)
                : new CandidatePrefetchService.PrefetchBatchResult(0, 0, 0, 0, List.of());

        List<StockCandidateMetrics> metrics = candidateScoreMapper.findCandidateMetrics(properties.sourceCandidateLimit());
        List<StockCandidateScore> scores = metrics.stream()
                .map(metric -> scoringEngine.score(metric, scoredDate))
                .sorted(Comparator.comparing(StockCandidateScore::getScore).reversed()
                        .thenComparing(StockCandidateScore::getStockCode))
                .toList();
        scores.forEach(candidateScoreMapper::upsert);

        if (addLimit <= 0 || scores.isEmpty()) {
            int watchedAfter = stockMasterMapper.countWatched();
            return new DiscoveryResult(
                    scoredDate,
                    scores.size(),
                    properties.aiCandidateLimit(),
                    properties.maxWatchedStocks(),
                    properties.maxDailyAdditions(),
                    watchedBefore,
                    watchedAfter,
                    slots,
                    bootstrap,
                    prefetchResult.targetCount(),
                    prefetchResult.succeededCount(),
                    prefetchResult.failedCount(),
                    prefetchResult.analyzedNewsCount(),
                    0,
                    List.of()
            );
        }

        List<StockCandidateScore> aiCandidates = scores.stream()
                .limit(properties.aiCandidateLimit())
                .toList();

        List<SelectedCandidate> selected = selectCandidates(aiCandidates, addLimit);
        List<SelectedStock> selectedStocks = new ArrayList<>();
        int bootstrappedCount = 0;

        for (SelectedCandidate candidate : selected) {
            StockCandidateScore score = candidate.score();
            watchStock(score);
            candidateScoreMapper.updateStatus(score.getStockCode(), scoredDate, candidate.source());

            boolean bootstrapSucceeded = true;
            if (bootstrap) {
                bootstrapSucceeded = bootstrapStock(score, newsAnalyzeLimit);
                if (bootstrapSucceeded) {
                    bootstrappedCount++;
                }
            }

            selectedStocks.add(new SelectedStock(
                    score.getStockCode(),
                    score.getStockName(),
                    score.getScore(),
                    candidate.source(),
                    candidate.reason(),
                    bootstrapSucceeded
            ));
        }

        enforceMaxWatched(accountNo);
        int watchedAfter = stockMasterMapper.countWatched();

        return new DiscoveryResult(
                scoredDate,
                scores.size(),
                properties.aiCandidateLimit(),
                properties.maxWatchedStocks(),
                properties.maxDailyAdditions(),
                watchedBefore,
                watchedAfter,
                slots,
                bootstrap,
                prefetchResult.targetCount(),
                prefetchResult.succeededCount(),
                prefetchResult.failedCount(),
                prefetchResult.analyzedNewsCount(),
                bootstrappedCount,
                selectedStocks
        );
    }

    public List<StockCandidateScore> findLatestCandidates(int limit) {
        int normalizedLimit = limit > 0 ? Math.min(limit, 200) : properties.aiCandidateLimit();
        return candidateScoreMapper.findLatestTop(LocalDate.now(), normalizedLimit);
    }

    private List<SelectedCandidate> selectCandidates(List<StockCandidateScore> candidates, int addLimit) {
        List<SelectedCandidate> selected = new ArrayList<>();
        selected.addAll(selectWithAi(candidates, addLimit));

        Set<String> selectedCodes = selected.stream()
                .map(item -> item.score().getStockCode())
                .collect(Collectors.toCollection(HashSet::new));

        candidates.stream()
                .filter(candidate -> selectedCodes.add(candidate.getStockCode()))
                .limit(addLimit - selected.size())
                .map(candidate -> new SelectedCandidate(candidate, "FALLBACK_SELECTED", "deterministic top score fallback"))
                .forEach(selected::add);

        return selected.stream()
                .limit(addLimit)
                .toList();
    }

    private List<SelectedCandidate> selectWithAi(List<StockCandidateScore> candidates, int addLimit) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        try {
            OpenAiResponse response = openAiDecisionClient.createTextCompletion(
                    buildSystemPrompt(addLimit),
                    buildUserPrompt(candidates),
                    openAiProperties.modelSummary()
            );
            String text = response.extractText();
            if (text == null || text.isBlank()) {
                return List.of();
            }

            Map<String, StockCandidateScore> byCode = candidates.stream()
                    .collect(Collectors.toMap(StockCandidateScore::getStockCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
            JsonNode selectedNode = objectMapper.readTree(stripCodeFence(text)).path("selected");
            if (!selectedNode.isArray()) {
                return List.of();
            }

            List<SelectedCandidate> selected = new ArrayList<>();
            Set<String> usedCodes = new HashSet<>();
            for (JsonNode item : selectedNode) {
                String stockCode = item.path("stockCode").asText(null);
                if (stockCode == null || !usedCodes.add(stockCode) || !byCode.containsKey(stockCode)) {
                    continue;
                }
                String reason = item.path("reason").asText("AI selected from scored candidates");
                selected.add(new SelectedCandidate(byCode.get(stockCode), "AI_SELECTED", reason));
                if (selected.size() >= addLimit) {
                    break;
                }
            }
            return selected;
        } catch (Exception e) {
            log.warn("[Watchlist] AI selection failed. fallback will be used: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSystemPrompt(int addLimit) {
        return """
                You select Korean stock watchlist candidates for an automated PAPER-first trading system.
                Choose at most %d stocks only from the provided candidate list.
                Do not invent stock codes.
                Prefer liquid stocks with strong technical/news signals and avoid overheated risk.
                Return compact JSON only: {"selected":[{"stockCode":"000000","reason":"short reason"}]}
                """.formatted(addLimit);
    }

    private String buildUserPrompt(List<StockCandidateScore> candidates) {
        String rows = candidates.stream()
                .map(candidate -> "%s | %s | score=%s | %s".formatted(
                        candidate.getStockCode(),
                        candidate.getStockName(),
                        candidate.getScore(),
                        candidate.getReason()
                ))
                .collect(Collectors.joining("\n"));
        return "Candidates:\n" + rows;
    }

    private String stripCodeFence(String text) {
        String stripped = text.trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("^```[a-zA-Z]*\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        return stripped.trim();
    }

    private void watchStock(StockCandidateScore score) {
        if (score.getCorpCode() != null && !score.getCorpCode().isBlank()) {
            stockService.upsertStockMasterFromDart(score.getStockCode(), score.getStockName(), score.getCorpCode());
        } else {
            stockService.upsertStockMaster(score.getStockCode(), score.getStockName(), "UNKNOWN");
        }
        stockService.updateWatched(score.getStockCode(), true);
    }

    private boolean bootstrapStock(StockCandidateScore score, int newsAnalyzeLimit) {
        try {
            StockDataBootstrapService.BootstrapResult result = stockDataBootstrapService.bootstrap(
                    score.getStockCode(),
                    LocalDate.now().minusMonths(3),
                    LocalDate.now(),
                    score.getStockName(),
                    DEFAULT_NEWS_DISPLAY,
                    Math.max(0, newsAnalyzeLimit)
            );
            return result.allSucceeded();
        } catch (Exception e) {
            log.warn("[Watchlist] bootstrap failed: stockCode={}, message={}", score.getStockCode(), e.getMessage());
            return false;
        }
    }

    private void enforceMaxWatched(String accountNo) {
        int overflow = stockMasterMapper.countWatched() - properties.maxWatchedStocks();
        if (overflow > 0) {
            stockMasterMapper.unwatchOverflowNonHeld(accountNo, overflow);
        }
    }

    private record SelectedCandidate(
            StockCandidateScore score,
            String source,
            String reason
    ) {
    }

    public record DiscoveryResult(
            LocalDate scoredDate,
            int scoredCount,
            int aiCandidateLimit,
            int maxWatchedStocks,
            int maxDailyAdditions,
            int watchedBefore,
            int watchedAfter,
            int availableSlots,
            boolean bootstrap,
            int prefetchedCount,
            int prefetchSucceededCount,
            int prefetchFailedCount,
            int prefetchAnalyzedNewsCount,
            int bootstrappedCount,
            List<SelectedStock> selectedStocks
    ) {
    }

    public record SelectedStock(
            String stockCode,
            String stockName,
            BigDecimal score,
            String selectionSource,
            String reason,
            boolean bootstrapSucceeded
    ) {
    }
}
