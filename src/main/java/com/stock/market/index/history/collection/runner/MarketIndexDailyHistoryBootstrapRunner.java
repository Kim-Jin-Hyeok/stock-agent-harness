package com.stock.market.index.history.collection.runner;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionResult;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionService;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryBootstrapProperties;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryCollectionProperties;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.LocalDate;
import java.util.Objects;

@Slf4j
public class MarketIndexDailyHistoryBootstrapRunner
        implements ApplicationRunner {
    private final MarketIndexDailyHistoryCollectionService collectionService;
    private final MarketIndexDailyHistoryCollectionDatePolicy
            collectionDatePolicy;
    private final MarketIndexDailyHistoryBootstrapProperties
            bootstrapProperties;
    private final MarketIndexDailyHistoryCollectionProperties
            collectionProperties;

    public MarketIndexDailyHistoryBootstrapRunner(
            MarketIndexDailyHistoryCollectionService collectionService,
            MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy,
            MarketIndexDailyHistoryBootstrapProperties bootstrapProperties,
            MarketIndexDailyHistoryCollectionProperties collectionProperties
    ) {
        this.collectionService = Objects.requireNonNull(
                collectionService,
                "collectionService must not be null."
        );
        this.collectionDatePolicy = Objects.requireNonNull(
                collectionDatePolicy,
                "collectionDatePolicy must not be null."
        );
        this.bootstrapProperties = Objects.requireNonNull(
                bootstrapProperties,
                "bootstrapProperties must not be null."
        );
        this.collectionProperties = Objects.requireNonNull(
                collectionProperties,
                "collectionProperties must not be null."
        );
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!bootstrapProperties.enabled()) {
            log.debug("Market index daily history bootstrap is disabled.");
            return;
        }

        LocalDate toDate = collectionDatePolicy
                .getLatestCompletedTradingDate();
        LocalDate fromDate = toDate.minusYears(
                collectionProperties.initialLookbackYears()
        );

        for (String benchmarkId : collectionProperties.benchmarkIds()) {
            collect(benchmarkId, fromDate, toDate);
        }
    }

    private void collect(
            String benchmarkId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        MarketIndexDailyHistoryCollectionResult result =
                collectionService.collect(
                        new MarketIndexDailyHistoryRequest(
                                benchmarkId,
                                fromDate,
                                toDate
                        )
                );
        log.info(
                "Market index daily history bootstrap completed. status={}, "
                        + "benchmarkId={}, requestedFromDate={}, "
                        + "requestedToDate={}, actualFromDate={}, "
                        + "fetchedCount={}, savedCount={}",
                result.status(),
                result.benchmarkId(),
                result.requestedFromDate(),
                result.requestedToDate(),
                result.actualFromDate(),
                result.fetchedCount(),
                result.savedCount()
        );
    }
}
