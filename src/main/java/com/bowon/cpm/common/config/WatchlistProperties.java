package com.bowon.cpm.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cpm.watchlist")
public record WatchlistProperties(
        int maxWatchedStocks,
        int maxDailyAdditions,
        int aiCandidateLimit,
        int sourceCandidateLimit,
        int prefetchCandidateLimit
) {
    public WatchlistProperties {
        if (maxWatchedStocks <= 0) {
            maxWatchedStocks = 20;
        }
        if (maxDailyAdditions <= 0) {
            maxDailyAdditions = 5;
        }
        if (aiCandidateLimit <= 0) {
            aiCandidateLimit = 30;
        }
        if (sourceCandidateLimit <= 0) {
            sourceCandidateLimit = 500;
        }
        if (prefetchCandidateLimit <= 0) {
            prefetchCandidateLimit = 30;
        }
    }
}
