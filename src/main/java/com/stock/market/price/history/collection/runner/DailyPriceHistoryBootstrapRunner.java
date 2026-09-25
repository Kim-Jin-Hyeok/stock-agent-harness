package com.stock.market.price.history.collection.runner;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionResult;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionService;
import com.stock.market.price.history.collection.config.DailyPriceHistoryBootstrapProperties;
import com.stock.market.price.history.collection.config.DailyPriceHistoryCollectionProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.LocalDate;
import java.util.Objects;

@Slf4j
public class DailyPriceHistoryBootstrapRunner implements ApplicationRunner {
    private final DailyPriceHistoryCollectionService collectionService;
    private final DailyPriceCollectionDatePolicy collectionDatePolicy;
    private final DailyPriceHistoryBootstrapProperties bootstrapProperties;
    private final DailyPriceHistoryCollectionProperties collectionProperties;

    public DailyPriceHistoryBootstrapRunner(
            DailyPriceHistoryCollectionService collectionService,
            DailyPriceCollectionDatePolicy collectionDatePolicy,
            DailyPriceHistoryBootstrapProperties bootstrapProperties,
            DailyPriceHistoryCollectionProperties collectionProperties
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
            log.debug("Daily price history bootstrap is disabled.");
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
        DailyPriceHistoryCollectionResult result = collectionService.collect(
                new DailyPriceHistoryRequest(symbol, fromDate, toDate)
        );
        log.info(
                "Daily price history bootstrap completed. status={}, "
                        + "symbol={}, requestedFromDate={}, requestedToDate={}, "
                        + "actualFromDate={}, fetchedCount={}, savedCount={}",
                result.status(),
                result.symbol(),
                result.requestedFromDate(),
                result.requestedToDate(),
                result.actualFromDate(),
                result.fetchedCount(),
                result.savedCount()
        );
    }
}
