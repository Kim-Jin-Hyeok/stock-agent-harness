package com.stock.market.price.history.collection.scheduler;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionResult;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionService;
import com.stock.market.price.history.collection.config.DailyPriceHistoryCollectionProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import com.stock.market.price.history.collection.scheduler.config.DailyPriceHistoryCollectionSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.Objects;

@Slf4j
public class DailyPriceHistoryCollectionScheduler {
    private final DailyPriceHistoryCollectionService collectionService;
    private final DailyPriceCollectionDatePolicy collectionDatePolicy;
    private final DailyPriceHistoryCollectionProperties collectionProperties;
    private final DailyPriceHistoryCollectionSchedulerProperties
            schedulerProperties;

    public DailyPriceHistoryCollectionScheduler(
            DailyPriceHistoryCollectionService collectionService,
            DailyPriceCollectionDatePolicy collectionDatePolicy,
            DailyPriceHistoryCollectionProperties collectionProperties,
            DailyPriceHistoryCollectionSchedulerProperties schedulerProperties
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
            cron = "${market.price.history.collection.scheduler.cron}",
            zone = "${market.price.history.collection.scheduler.zone-id}"
    )
    public void run() {
        if (!schedulerProperties.enabled()) {
            log.debug("Daily price history collection scheduler is disabled.");
            return;
        }

        LocalDate toDate = collectionDatePolicy
                .getLatestCompletedTradingDate();
        LocalDate fromDate = toDate.minusYears(
                collectionProperties.initialLookbackYears()
        );

        for (String symbol : collectionProperties.symbols()) {
            collect(symbol, fromDate, toDate);
        }
    }

    private void collect(
            String symbol,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        try {
            DailyPriceHistoryCollectionResult result =
                    collectionService.collect(
                            new DailyPriceHistoryRequest(
                                    symbol,
                                    fromDate,
                                    toDate
                            )
                    );
            log.info(
                    "Daily price history scheduled collection completed. "
                            + "status={}, symbol={}, requestedFromDate={}, "
                            + "requestedToDate={}, actualFromDate={}, "
                            + "fetchedCount={}, savedCount={}",
                    result.status(),
                    result.symbol(),
                    result.requestedFromDate(),
                    result.requestedToDate(),
                    result.actualFromDate(),
                    result.fetchedCount(),
                    result.savedCount()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Daily price history scheduled collection failed. "
                            + "symbol={}, failureType={}, failureMessage={}",
                    symbol,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }
    }
}
