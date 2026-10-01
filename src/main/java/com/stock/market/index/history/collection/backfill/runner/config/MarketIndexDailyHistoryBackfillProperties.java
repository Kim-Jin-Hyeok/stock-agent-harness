package com.stock.market.index.history.collection.backfill.runner.config;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties(prefix = "market.index.history.collection.backfill")
public record MarketIndexDailyHistoryBackfillProperties(
        boolean enabled,
        String benchmarkId,
        LocalDate fromDate,
        LocalDate toDate
) {
    public MarketIndexDailyHistoryBackfillProperties {
        if (enabled) {
            new MarketIndexDailyHistoryRequest(benchmarkId, fromDate, toDate);
        }
    }

    public MarketIndexDailyHistoryRequest toRequest() {
        return new MarketIndexDailyHistoryRequest(benchmarkId, fromDate, toDate);
    }
}
