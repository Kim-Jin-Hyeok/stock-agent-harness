package com.stock.strategy.universe.liquidity;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DailyTradingValueAverageCalculator {
    // Callers must verify the trading calendar, source units and point-in-time availability.
    public Optional<DailyTradingValueAverage> calculate(
            DailyPriceHistory history,
            LocalDate selectionAsOfDate,
            List<LocalDate> requiredTradingDates,
            TradingVenueScope expectedVenueScope
    ) {
        Objects.requireNonNull(history, "history must not be null.");
        Objects.requireNonNull(expectedVenueScope, "expectedVenueScope must not be null.");
        List<LocalDate> tradingDates = DailyTradingValueAverage.copyValidatedTradingDates(
                selectionAsOfDate,
                requiredTradingDates
        );
        Map<LocalDate, DailyPriceBar> barsByDate = history.bars().stream()
                .collect(Collectors.toMap(DailyPriceBar::tradingDate, Function.identity()));

        BigInteger totalTradingValueKrw = BigInteger.ZERO;
        boolean incomplete = false;
        for (LocalDate tradingDate : tradingDates) {
            DailyPriceBar bar = barsByDate.get(tradingDate);
            if (bar == null) {
                incomplete = true;
                continue;
            }
            // Missing data must not mask a known venue conflict on another required date.
            if (bar.tradingVenueScope() != null
                    && bar.tradingVenueScope() != expectedVenueScope) {
                throw new IllegalArgumentException(
                        "tradingVenueScope must match expectedVenueScope. tradingDate="
                                + tradingDate
                );
            }
            if (bar.tradingValueKrw() == null || bar.tradingVenueScope() == null) {
                incomplete = true;
                continue;
            }
            totalTradingValueKrw = totalTradingValueKrw.add(
                    BigInteger.valueOf(bar.tradingValueKrw())
            );
        }
        if (incomplete) {
            return Optional.empty();
        }

        return Optional.of(new DailyTradingValueAverage(
                history.symbol(),
                selectionAsOfDate,
                tradingDates,
                expectedVenueScope,
                totalTradingValueKrw
        ));
    }
}
