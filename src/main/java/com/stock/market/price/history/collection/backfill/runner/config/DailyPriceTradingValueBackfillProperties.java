package com.stock.market.price.history.collection.backfill.runner.config;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@ConfigurationProperties(prefix = "market.price.history.collection.trading-value-backfill")
public record DailyPriceTradingValueBackfillProperties(
        boolean enabled,
        String symbol,
        LocalDate fromDate,
        LocalDate toDate,
        TradingVenueScope expectedVenueScope,
        Integer maxRangeDays
) {
    public DailyPriceTradingValueBackfillProperties {
        if (enabled) {
            new DailyPriceHistoryRequest(symbol, fromDate, toDate);
            Objects.requireNonNull(expectedVenueScope, "expectedVenueScope must not be null.");
            Objects.requireNonNull(maxRangeDays, "maxRangeDays must not be null.");
            if (maxRangeDays <= 0) {
                throw new IllegalArgumentException("maxRangeDays must be positive.");
            }
            long requestedDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
            if (requestedDays > maxRangeDays) {
                throw new IllegalArgumentException(
                        "Backfill range must not exceed maxRangeDays (inclusive calendar days)."
                );
            }
        }
    }

    public DailyPriceHistoryRequest toRequest() {
        if (!enabled) {
            throw new IllegalStateException("Trading value backfill must be enabled to create a request.");
        }
        return new DailyPriceHistoryRequest(symbol, fromDate, toDate);
    }
}
