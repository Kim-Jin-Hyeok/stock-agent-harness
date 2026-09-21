package com.stock.harness.scheduler.policy;

import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class StrategyExecutionDatePolicy {
    private final MarketTradingDayPolicy marketTradingDayPolicy;

    public boolean isExecutionDate(
            InvestmentStrategyIdentity strategyIdentity,
            LocalDate evaluatedDate
    ) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        Objects.requireNonNull(evaluatedDate, "evaluatedDate must not be null.");

        if (!marketTradingDayPolicy.isTradingDay(evaluatedDate)) {
            return false;
        }

        return switch (strategyIdentity.horizon()) {
            case DAY_TRADING, SWING -> true;
            case LONG_TERM -> isLastTradingDayOfWeek(evaluatedDate);
        };
    }

    private boolean isLastTradingDayOfWeek(LocalDate evaluatedDate) {
        LocalDate weekEnd = evaluatedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate nextDate = evaluatedDate.plusDays(1);

        while (!nextDate.isAfter(weekEnd)) {
            if (marketTradingDayPolicy.isTradingDay(nextDate)) {
                return false;
            }
            nextDate = nextDate.plusDays(1);
        }

        return true;
    }
}
