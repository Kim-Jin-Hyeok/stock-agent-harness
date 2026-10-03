package com.stock.strategy.universe.liquidity.evaluation.runner.config;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "strategy.universe.liquidity.evaluation.manual")
public record DailyTradingValueSelectionEvaluationProperties(
        boolean enabled,
        List<String> targetSymbols,
        LocalDate selectionAsOfDate,
        List<LocalDate> requiredTradingDates,
        TradingVenueScope expectedVenueScope,
        Long minimumAverageTradingValueKrw,
        Integer maxCandidateCount
) {
    public DailyTradingValueSelectionEvaluationProperties {
        targetSymbols = targetSymbols == null ? List.of() : List.copyOf(targetSymbols);
        requiredTradingDates = requiredTradingDates == null ? List.of() : List.copyOf(requiredTradingDates);
        if (enabled) {
            Objects.requireNonNull(minimumAverageTradingValueKrw, "minimumAverageTradingValueKrw must not be null.");
            Objects.requireNonNull(maxCandidateCount, "maxCandidateCount must not be null.");
            var request = new DailyTradingValueSelectionEvaluationRequest(
                    targetSymbols, selectionAsOfDate, requiredTradingDates, expectedVenueScope,
                    minimumAverageTradingValueKrw, maxCandidateCount
            );
            targetSymbols = request.targetSymbols();
            requiredTradingDates = request.requiredTradingDates();
        }
    }

    public DailyTradingValueSelectionEvaluationRequest toRequest() {
        if (!enabled) {
            throw new IllegalStateException("Manual trading value selection evaluation must be enabled to create a request.");
        }
        return new DailyTradingValueSelectionEvaluationRequest(
                targetSymbols, selectionAsOfDate, requiredTradingDates, expectedVenueScope,
                minimumAverageTradingValueKrw, maxCandidateCount
        );
    }
}
