package com.stock.market.price.history.collection.backfill.runner;

import com.stock.broker.kis.config.KisProperties;
import com.stock.market.price.history.collection.backfill.DailyPriceTradingValueBackfillService;
import com.stock.market.price.history.collection.backfill.runner.config.DailyPriceTradingValueBackfillProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "market.price.history.collection.trading-value-backfill",
        name = "enabled",
        havingValue = "true"
)
public class DailyPriceTradingValueBackfillRunner implements ApplicationRunner {
    private final DailyPriceTradingValueBackfillService backfillService;
    private final DailyPriceTradingValueBackfillProperties properties;
    private final DailyPriceCollectionDatePolicy collectionDatePolicy;
    private final KisProperties kisProperties;

    public DailyPriceTradingValueBackfillRunner(
            DailyPriceTradingValueBackfillService backfillService,
            DailyPriceTradingValueBackfillProperties properties,
            DailyPriceCollectionDatePolicy collectionDatePolicy,
            KisProperties kisProperties
    ) {
        this.backfillService = Objects.requireNonNull(backfillService, "backfillService must not be null.");
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
        this.collectionDatePolicy = Objects.requireNonNull(collectionDatePolicy, "collectionDatePolicy must not be null.");
        this.kisProperties = Objects.requireNonNull(kisProperties, "kisProperties must not be null.");
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            return;
        }
        var request = properties.toRequest();
        if (!kisProperties.enabled()) {
            throw new IllegalStateException("Trading value backfill requires broker.kis.enabled=true.");
        }
        if (properties.expectedVenueScope()
                != kisProperties.dailyPriceHistoryMarket().toTradingVenueScope()) {
            throw new IllegalArgumentException(
                    "Backfill expectedVenueScope must match broker.kis.daily-price-history-market."
            );
        }
        LocalDate latestCompletedDate = Objects.requireNonNull(
                collectionDatePolicy.getLatestCompletedTradingDate(),
                "latestCompletedTradingDate must not be null."
        );
        if (request.toDate().isAfter(latestCompletedDate)) {
            throw new IllegalArgumentException(
                    "Backfill toDate must not be after the latest completed trading date."
            );
        }

        log.info(
                "Daily price trading value backfill started. request={}, expectedVenueScope={}, "
                        + "maxRangeDays={}, latestCompletedTradingDate={}, maxPages={}, requestDelay={}",
                request, properties.expectedVenueScope(), properties.maxRangeDays(), latestCompletedDate,
                kisProperties.dailyPriceHistoryMaxPages(), kisProperties.dailyPriceHistoryRequestDelay()
        );
        var result = backfillService.backfill(request, properties.expectedVenueScope());
        log.info(
                "Daily price trading value backfill finished. status={}, requestedRange={}, "
                        + "collectionRange={}, expectedVenueScope={}, targetCount={}, fetchedCount={}, updatedCount={}",
                result.status(), result.requestedRange(), result.collectionRange(), result.expectedVenueScope(),
                result.targetCount(), result.fetchedCount(), result.updatedCount()
        );
    }
}
