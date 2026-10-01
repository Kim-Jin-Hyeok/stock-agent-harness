package com.stock.backtest.comparison.buyandhold.model;

import com.stock.trade.cost.model.TradeCostModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public record BuyAndHoldBacktestRequest(
        String symbol,
        long initialCashAmountKrw,
        BigDecimal initialAllocationRatio,
        List<Instant> valuationInstants,
        TradeCostModel costModel
) {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    public BuyAndHoldBacktestRequest {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (initialCashAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "initialCashAmountKrw must be positive."
            );
        }
        Objects.requireNonNull(
                initialAllocationRatio,
                "initialAllocationRatio must not be null."
        );
        if (initialAllocationRatio.signum() <= 0
                || initialAllocationRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "initialAllocationRatio must be greater than 0 "
                            + "and at most 1."
            );
        }
        valuationInstants = List.copyOf(Objects.requireNonNull(
                valuationInstants,
                "valuationInstants must not be null."
        ));
        if (valuationInstants.isEmpty()) {
            throw new IllegalArgumentException(
                    "valuationInstants must not be empty."
            );
        }
        LocalDate previousDate = null;
        for (Instant instant : valuationInstants) {
            LocalDate date = instant.atZone(MARKET_ZONE).toLocalDate();
            if (previousDate != null && !date.isAfter(previousDate)) {
                throw new IllegalArgumentException(
                        "valuationInstants must be ordered by unique "
                                + "valuation date in Asia/Seoul."
                );
            }
            previousDate = date;
        }
        Objects.requireNonNull(costModel, "costModel must not be null.");
    }

    public List<LocalDate> valuationDates() {
        return valuationInstants.stream()
                .map(instant -> instant.atZone(MARKET_ZONE).toLocalDate())
                .toList();
    }

    public long buyBudgetAmountKrw() {
        return BigDecimal.valueOf(initialCashAmountKrw)
                .multiply(initialAllocationRatio)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }
}
