package com.stock.strategy.universe.liquidity;

import com.stock.market.price.history.TradingVenueScope;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record DailyTradingValueAverage(
        String symbol,
        LocalDate selectionAsOfDate,
        List<LocalDate> tradingDates,
        TradingVenueScope tradingVenueScope,
        BigInteger totalTradingValueKrw
) {
    public DailyTradingValueAverage {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        tradingDates = copyValidatedTradingDates(selectionAsOfDate, tradingDates);
        Objects.requireNonNull(tradingVenueScope, "tradingVenueScope must not be null.");
        Objects.requireNonNull(totalTradingValueKrw, "totalTradingValueKrw must not be null.");
        if (totalTradingValueKrw.signum() < 0) {
            throw new IllegalArgumentException("totalTradingValueKrw must not be negative.");
        }
    }

    public int tradingDayCount() {
        return tradingDates.size();
    }

    public boolean meetsMinimumAverageTradingValueKrw(long minimumAverageTradingValueKrw) {
        if (minimumAverageTradingValueKrw <= 0) {
            throw new IllegalArgumentException(
                    "minimumAverageTradingValueKrw must be positive."
            );
        }
        BigInteger minimumTotalTradingValueKrw = BigInteger
                .valueOf(minimumAverageTradingValueKrw)
                .multiply(BigInteger.valueOf(tradingDayCount()));
        return totalTradingValueKrw.compareTo(minimumTotalTradingValueKrw) >= 0;
    }

    // Validates the supplied date structure, not the exchange's trading calendar.
    public static List<LocalDate> copyValidatedTradingDates(
            LocalDate selectionAsOfDate,
            List<LocalDate> tradingDates
    ) {
        Objects.requireNonNull(selectionAsOfDate, "selectionAsOfDate must not be null.");
        Objects.requireNonNull(tradingDates, "tradingDates must not be null.");
        List<LocalDate> dates = new ArrayList<>(tradingDates);
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("tradingDates must not be empty.");
        }

        LocalDate previousDate = null;
        for (LocalDate date : dates) {
            Objects.requireNonNull(date, "tradingDate must not be null.");
            if (date.isAfter(selectionAsOfDate)) {
                throw new IllegalArgumentException(
                        "tradingDates must not be after selectionAsOfDate."
                );
            }
            if (previousDate != null && !date.isAfter(previousDate)) {
                throw new IllegalArgumentException("tradingDates must be strictly increasing.");
            }
            previousDate = date;
        }
        if (!dates.getLast().equals(selectionAsOfDate)) {
            throw new IllegalArgumentException("tradingDates must end on selectionAsOfDate.");
        }
        return List.copyOf(dates);
    }
}
