package com.stock.harness.scheduler.policy;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

@Component
public class StrategyRunCadencePolicy {
    private static final Duration DAY_TRADING_INTERVAL = Duration.ofMinutes(5);

    public boolean isDue(
            InvestmentStrategyIdentity strategyIdentity,
            LocalDateTime evaluatedAt,
            Optional<LocalDateTime> latestRunStartedAt
    ) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        Objects.requireNonNull(latestRunStartedAt, "latestRunStartedAt must not be null.");

        if (latestRunStartedAt.isEmpty()) {
            return true;
        }

        LocalDateTime lastStartedAt = latestRunStartedAt.get();
        if (lastStartedAt.isAfter(evaluatedAt)) {
            return false;
        }

        return switch (strategyIdentity.horizon()) {
            case DAY_TRADING -> !lastStartedAt.plus(DAY_TRADING_INTERVAL).isAfter(evaluatedAt);
            case SWING -> lastStartedAt.toLocalDate().isBefore(evaluatedAt.toLocalDate());
            case LONG_TERM -> weekStartDate(lastStartedAt.toLocalDate())
                    .isBefore(weekStartDate(evaluatedAt.toLocalDate()));
        };
    }

    private LocalDate weekStartDate(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
