package com.stock.strategy.universe.candidate.evaluation.support;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.candidate.evaluation.StockCandidateEvaluationService;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.eligibility.StockEligibilityPolicy;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockListingStatus;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class StockCandidateEvaluationFixture {
    public static final LocalDate DATE = LocalDate.of(2026, 9, 23);
    public static final Instant CUTOFF = Instant.parse("2026-09-23T09:00:00Z");
    public static final List<LocalDate> DATES = List.of(DATE.minusDays(2), DATE.minusDays(1), DATE);

    private StockCandidateEvaluationFixture() {}

    public static StockEligibilityRequest eligibilityRequest() {
        return new StockEligibilityRequest(DATE, CUTOFF, Set.of(StockMarket.KOSPI),
                Set.of(StockSecurityType.COMMON_STOCK));
    }

    public static StockCandidateEvaluationRequest request(String... symbols) {
        return request(100L, 2, symbols);
    }

    public static StockCandidateEvaluationRequest request(long minimum, int limit, String... symbols) {
        return new StockCandidateEvaluationRequest(eligibilityRequest(),
                new DailyTradingValueSelectionEvaluationRequest(List.of(symbols), DATE, DATES,
                        TradingVenueScope.INTEGRATED, minimum, limit));
    }

    public static StockEligibilityInput eligible(String symbol) {
        return input(symbol, StockSecurityType.COMMON_STOCK, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
    }

    public static StockEligibilityInput input(
            String symbol, StockSecurityType type, StockEligibilityEvidenceStatus evidence
    ) {
        return new StockEligibilityInput(symbol, DATE, StockMarket.KOSPI, type, StockListingStatus.LISTED,
                "synthetic-source-01", CUTOFF.minusSeconds(1), evidence);
    }

    public static DailyPriceHistory history(String symbol, long perDayValue) {
        return history(symbol, perDayValue, TradingVenueScope.INTEGRATED);
    }

    public static DailyPriceHistory history(String symbol, Long perDayValue, TradingVenueScope venue) {
        return new DailyPriceHistory(symbol, DATES.stream()
                .map(date -> new DailyPriceBar(date, 100L, 110L, 90L, 100L, 10L, perDayValue, venue))
                .toList());
    }

    public static DailyTradingValueSelectionEvaluationService liquidityService() {
        return new DailyTradingValueSelectionEvaluationService(new DailyTradingValueAverageCalculator(),
                new DailyTradingValueSelectionPolicy(new DailyTradingValueRankingPolicy()));
    }

    public static StockCandidateEvaluationService service() {
        return new StockCandidateEvaluationService(new StockEligibilityPolicy(), liquidityService());
    }
}
