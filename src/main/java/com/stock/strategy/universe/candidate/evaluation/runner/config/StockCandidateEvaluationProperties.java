package com.stock.strategy.universe.candidate.evaluation.runner.config;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "strategy.universe.candidate.evaluation.manual")
public record StockCandidateEvaluationProperties(
        boolean enabled,
        List<String> targetSymbols,
        LocalDate selectionAsOfDate,
        Instant selectionCutoffAt,
        Set<StockMarket> eligibleMarkets,
        Set<StockSecurityType> eligibleSecurityTypes,
        List<LocalDate> requiredTradingDates,
        TradingVenueScope expectedVenueScope,
        Long minimumAverageTradingValueKrw,
        Integer maxCandidateCount,
        List<StockEligibilityInput> eligibilityInputs
) {
    public StockCandidateEvaluationProperties {
        targetSymbols = targetSymbols == null ? List.of() : List.copyOf(targetSymbols);
        requiredTradingDates = requiredTradingDates == null ? List.of() : List.copyOf(requiredTradingDates);
        eligibleMarkets = eligibleMarkets == null ? Set.of() : Set.copyOf(eligibleMarkets);
        eligibleSecurityTypes = eligibleSecurityTypes == null ? Set.of() : Set.copyOf(eligibleSecurityTypes);
        eligibilityInputs = eligibilityInputs == null ? List.of() : List.copyOf(eligibilityInputs);
        if (enabled) {
            Objects.requireNonNull(minimumAverageTradingValueKrw, "minimumAverageTradingValueKrw must not be null.");
            Objects.requireNonNull(maxCandidateCount, "maxCandidateCount must not be null.");
            var eligibilityRequest = new StockEligibilityRequest(
                    selectionAsOfDate, selectionCutoffAt, eligibleMarkets, eligibleSecurityTypes
            );
            var liquidityRequest = new DailyTradingValueSelectionEvaluationRequest(
                    targetSymbols, selectionAsOfDate, requiredTradingDates, expectedVenueScope,
                    minimumAverageTradingValueKrw, maxCandidateCount
            );
            targetSymbols = liquidityRequest.targetSymbols();
            requiredTradingDates = liquidityRequest.requiredTradingDates();
            eligibleMarkets = eligibilityRequest.eligibleMarkets();
            eligibleSecurityTypes = eligibilityRequest.eligibleSecurityTypes();
            Set<String> targets = new HashSet<>(targetSymbols);
            Set<String> suppliedSymbols = new HashSet<>();
            for (StockEligibilityInput input : eligibilityInputs) {
                if (!targets.contains(input.symbol())) {
                    throw new IllegalArgumentException("Eligibility input symbol must belong to targetSymbols: "
                            + input.symbol());
                }
                if (!suppliedSymbols.add(input.symbol())) {
                    throw new IllegalArgumentException("Duplicate eligibility input symbol: " + input.symbol());
                }
            }
        }
    }

    public StockCandidateEvaluationRequest toRequest() {
        if (!enabled) {
            throw new IllegalStateException("Manual stock candidate evaluation must be enabled to create a request.");
        }
        return new StockCandidateEvaluationRequest(
                new StockEligibilityRequest(selectionAsOfDate, selectionCutoffAt, eligibleMarkets, eligibleSecurityTypes),
                new DailyTradingValueSelectionEvaluationRequest(targetSymbols, selectionAsOfDate, requiredTradingDates,
                        expectedVenueScope, minimumAverageTradingValueKrw, maxCandidateCount)
        );
    }
}
