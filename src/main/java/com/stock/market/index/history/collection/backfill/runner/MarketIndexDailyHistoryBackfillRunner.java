package com.stock.market.index.history.collection.backfill.runner;

import com.stock.market.index.history.collection.backfill.MarketIndexDailyHistoryBackfillService;
import com.stock.market.index.history.collection.backfill.runner.config.MarketIndexDailyHistoryBackfillProperties;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "market.index.history.collection.backfill",
        name = "enabled",
        havingValue = "true"
)
public class MarketIndexDailyHistoryBackfillRunner implements ApplicationRunner {
    private final MarketIndexDailyHistoryBackfillService backfillService;
    private final MarketIndexDailyHistoryBackfillProperties properties;
    private final MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy;

    public MarketIndexDailyHistoryBackfillRunner(
            MarketIndexDailyHistoryBackfillService backfillService,
            MarketIndexDailyHistoryBackfillProperties properties,
            MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy
    ) {
        this.backfillService = Objects.requireNonNull(
                backfillService, "backfillService must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties, "properties must not be null."
        );
        this.collectionDatePolicy = Objects.requireNonNull(
                collectionDatePolicy, "collectionDatePolicy must not be null."
        );
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            return;
        }
        var request = properties.toRequest();
        if (request.toDate().isAfter(collectionDatePolicy.getLatestCompletedTradingDate())) {
            throw new IllegalArgumentException(
                    "Backfill toDate must not be after the latest completed trading date."
            );
        }
        log.info("Market index daily history backfill started. request={}", request);
        var result = backfillService.backfill(request);
        log.info(
                "Market index daily history backfill finished. status={}, "
                        + "requestedRange={}, collectionRange={}, fetchedCount={}, "
                        + "savedCount={}, fetchedFromDate={}, fetchedToDate={}",
                result.status(), result.requestedRange(), result.collectionRange(),
                result.fetchedCount(), result.savedCount(),
                result.fetchedFromDate(), result.fetchedToDate()
        );
    }
}
