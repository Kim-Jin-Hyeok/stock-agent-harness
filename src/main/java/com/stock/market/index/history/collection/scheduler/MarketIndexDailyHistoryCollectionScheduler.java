package com.stock.market.index.history.collection.scheduler;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionResult;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionService;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryCollectionProperties;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import com.stock.market.index.history.collection.scheduler.config.MarketIndexDailyHistoryCollectionSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.Objects;

@Slf4j
public class MarketIndexDailyHistoryCollectionScheduler {
    private final MarketIndexDailyHistoryCollectionService collectionService;
    private final MarketIndexDailyHistoryCollectionDatePolicy
            collectionDatePolicy;
    private final MarketIndexDailyHistoryCollectionProperties
            collectionProperties;
    private final MarketIndexDailyHistoryCollectionSchedulerProperties
            schedulerProperties;

    public MarketIndexDailyHistoryCollectionScheduler(
            MarketIndexDailyHistoryCollectionService collectionService,
            MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy,
            MarketIndexDailyHistoryCollectionProperties collectionProperties,
            MarketIndexDailyHistoryCollectionSchedulerProperties
                    schedulerProperties
    ) {
        this.collectionService = Objects.requireNonNull(
                collectionService,
                "collectionService must not be null."
        );
        this.collectionDatePolicy = Objects.requireNonNull(
                collectionDatePolicy,
                "collectionDatePolicy must not be null."
        );
        this.collectionProperties = Objects.requireNonNull(
                collectionProperties,
                "collectionProperties must not be null."
        );
        this.schedulerProperties = Objects.requireNonNull(
                schedulerProperties,
                "schedulerProperties must not be null."
        );
    }

    @Scheduled(
            cron = "${market.index.history.collection.scheduler.cron}",
            zone = "${market.index.history.collection.scheduler.zone-id}"
    )
    public void run() {
        if (!schedulerProperties.enabled()) {
            log.debug("Market index daily history collection scheduler is disabled.");
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
        try {
            MarketIndexDailyHistoryCollectionResult result =
                    collectionService.collect(
                            new MarketIndexDailyHistoryRequest(
                                    benchmarkId,
                                    fromDate,
                                    toDate
                            )
                    );
            log.info(
                    "Market index daily history scheduled collection completed. "
                            + "status={}, benchmarkId={}, requestedFromDate={}, "
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
        } catch (RuntimeException exception) {
            log.error(
                    "Market index daily history scheduled collection failed. "
                            + "benchmarkId={}, failureType={}, failureMessage={}",
                    benchmarkId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }
    }
}
