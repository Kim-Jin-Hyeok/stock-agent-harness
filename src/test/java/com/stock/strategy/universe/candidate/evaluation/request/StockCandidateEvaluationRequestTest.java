package com.stock.strategy.universe.candidate.evaluation.request;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.CUTOFF;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATES;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligibilityRequest;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCandidateEvaluationRequestTest {
    @Test
    void preservesOriginalRequestsAndNormalizedFullTargetSet() {
        StockEligibilityRequest eligibility = eligibilityRequest();
        DailyTradingValueSelectionEvaluationRequest liquidity = request("005930", "000660").liquidityRequest();
        StockCandidateEvaluationRequest combined = new StockCandidateEvaluationRequest(eligibility, liquidity);

        assertThat(combined.eligibilityRequest()).isSameAs(eligibility);
        assertThat(combined.liquidityRequest()).isSameAs(liquidity);
        assertThat(combined.targetSymbols()).containsExactly("000660", "005930");
        assertThatThrownBy(combined.targetSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void rejectsDifferentAsOfDates(int difference) {
        StockEligibilityRequest eligibility = new StockEligibilityRequest(
                DATE.plusDays(difference), CUTOFF.plusSeconds(86400),
                Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK)
        );

        assertThatThrownBy(() -> new StockCandidateEvaluationRequest(
                eligibility, request("005930").liquidityRequest()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility and liquidity selectionAsOfDate must match.");
    }

    @Test
    void requiresBothRequests() {
        assertThatThrownBy(() -> new StockCandidateEvaluationRequest(null, request("005930").liquidityRequest()))
                .isInstanceOf(NullPointerException.class).hasMessage("eligibilityRequest must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationRequest(eligibilityRequest(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("liquidityRequest must not be null.");
    }

    @Test
    void buildsEligibleSubsetWithoutChangingCriteriaOrFullTargets() {
        DailyTradingValueSelectionEvaluationRequest original = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930", "000660", "035420"), DATE, DATES, TradingVenueScope.KRX, 987L, 7
        );
        StockCandidateEvaluationRequest combined = new StockCandidateEvaluationRequest(eligibilityRequest(), original);
        List<String> subset = new ArrayList<>(List.of("035420", "005930"));

        DailyTradingValueSelectionEvaluationRequest filtered = combined.liquidityRequestFor(subset);
        subset.clear();

        assertThat(filtered).isEqualTo(new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930", "035420"), DATE, DATES, TradingVenueScope.KRX, 987L, 7
        ));
        assertThat(combined.targetSymbols()).containsExactly("000660", "005930", "035420");
        assertThat(combined.liquidityRequest()).isSameAs(original);
    }

    @Test
    void rejectsSubsetOutsideOriginalTargets() {
        assertThatThrownBy(() -> request("005930").liquidityRequestFor(List.of("000660")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eligibleSymbols must belong to request targetSymbols.");
    }

    @Test
    void preservesExistingEmptyAndDuplicateTargetRestrictions() {
        StockCandidateEvaluationRequest combined = request("005930");
        assertThatThrownBy(() -> combined.liquidityRequestFor(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("targetSymbols must not be empty.");
        assertThatThrownBy(() -> combined.liquidityRequestFor(List.of("005930", "005930")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate target symbol: 005930");
        assertThatThrownBy(() -> combined.liquidityRequestFor(null))
                .isInstanceOf(NullPointerException.class).hasMessage("eligibleSymbols must not be null.");
    }
}
