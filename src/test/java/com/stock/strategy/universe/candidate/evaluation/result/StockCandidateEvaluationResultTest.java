package com.stock.strategy.universe.candidate.evaluation.result;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.eligibility.StockEligibilityPolicy;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.CUTOFF;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATES;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.input;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.liquidityService;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCandidateEvaluationResultTest {
    @Test
    void preservesAllTargetsAndHistoriesWithImmutableCanonicalListsAndRankOrderedCandidates() {
        StockCandidateEvaluationRequest criteria = request("069500", "005930", "000660");
        List<StockEligibilityResult> eligibility = new ArrayList<>(List.of(
                eligibility(criteria, excluded("069500")),
                eligibility(criteria, eligible("005930")),
                eligibility(criteria, eligible("000660"))));
        List<DailyPriceHistory> histories = new ArrayList<>(List.of(
                history("069500", 9000L), history("005930", 200L), history("000660", 100L)));
        DailyTradingValueSelectionEvaluationResult liquidity = liquidityService().evaluate(
                criteria.liquidityRequestFor(List.of("005930", "000660")), histories.subList(1, 3));

        StockCandidateEvaluationResult result = new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.COMPLETE, eligibility, histories, liquidity);

        assertThat(result.request()).isSameAs(criteria);
        assertThat(result.liquidityResult()).isSameAs(liquidity);
        assertThat(result.eligibilityResults()).extracting(value -> value.input().symbol())
                .containsExactly("000660", "005930", "069500");
        assertThat(result.inputHistories()).extracting(DailyPriceHistory::symbol)
                .containsExactly("000660", "005930", "069500");
        assertThat(result.candidateSymbols()).containsExactly("005930", "000660");
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(eligibility.getFirst().input().symbol()).isEqualTo("069500");
        assertThat(histories.getFirst().symbol()).isEqualTo("069500");
        eligibility.clear();
        histories.clear();
        assertThat(result.eligibilityResults()).hasSize(3);
        assertThat(result.inputHistories()).hasSize(3);
        assertThatThrownBy(result.eligibilityResults()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.inputHistories()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.candidateSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unverifiedEligibilityRequiresIncompleteAndNoLiquidityEvenWhenAnotherTargetIsEligible() {
        StockCandidateEvaluationRequest criteria = request("005930", "000660");
        List<StockEligibilityResult> eligibility = List.of(eligibility(criteria, eligible("005930")),
                eligibility(criteria, input("000660", StockSecurityType.COMMON_STOCK,
                        StockEligibilityEvidenceStatus.CURRENT_ONLY)));
        StockCandidateEvaluationResult result = new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.INCOMPLETE, eligibility, List.of(), null);

        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.candidateSymbols()).isEmpty();
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.COMPLETE, eligibility, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unverified eligibility requires INCOMPLETE status and no liquidity result.");
        DailyTradingValueSelectionEvaluationResult liquidity = liquidityService().evaluate(
                criteria.liquidityRequestFor(List.of("005930")), List.of());
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.INCOMPLETE, eligibility, List.of(), liquidity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unverified eligibility requires INCOMPLETE status and no liquidity result.");
    }

    @Test
    void allConfirmedIneligibleRequiresCompleteAndNoLiquidity() {
        StockCandidateEvaluationRequest criteria = request("069500");
        List<StockEligibilityResult> eligibility = List.of(eligibility(criteria, excluded("069500")));
        StockCandidateEvaluationResult result = new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.COMPLETE, eligibility, List.of(), null);

        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.INCOMPLETE, eligibility, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("All ineligible targets require COMPLETE status and no liquidity result.");
        DailyTradingValueSelectionEvaluationResult liquidity = liquidityService().evaluate(
                criteria.liquidityRequest(), List.of());
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.COMPLETE, eligibility, List.of(), liquidity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("All ineligible targets require COMPLETE status and no liquidity result.");
    }

    @Test
    void eligibilityResultsMustExactlyCoverRequestedSymbolsWithoutDuplicates() {
        StockCandidateEvaluationRequest criteria = request("005930", "000660");
        StockEligibilityResult samsung = eligibility(criteria, eligible("005930"));
        StockEligibilityResult other = eligibility(criteria, eligible("035420"));

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, List.of(samsung), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility results must exactly cover request targetSymbols.");
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, List.of(samsung, other), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility results must exactly cover request targetSymbols.");
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, List.of(samsung, samsung), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate eligibility result symbol: 005930");
    }

    @Test
    void rejectsEligibilityResultWithDifferentCriteria() {
        StockCandidateEvaluationRequest criteria = request("005930");
        StockEligibilityRequest changed = new StockEligibilityRequest(DATE, CUTOFF.plusSeconds(1),
                criteria.eligibilityRequest().eligibleMarkets(), criteria.eligibilityRequest().eligibleSecurityTypes());
        StockEligibilityResult wrong = new StockEligibilityPolicy().evaluate(changed, eligible("005930"));

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, List.of(wrong), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility result request must match evaluation criteria.");
    }

    @Test
    void rejectsDuplicateOrOffTargetHistoriesEvenWhenNoLiquidityEvaluationIsRequired() {
        StockCandidateEvaluationRequest criteria = request("069500");
        List<StockEligibilityResult> eligibility = List.of(eligibility(criteria, excluded("069500")));

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, eligibility,
                List.of(history("069500", 100L), history("069500", 200L)), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate input history symbol: 069500");
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, eligibility, List.of(history("005930", 100L)), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input history symbol must belong to request targetSymbols: 005930");
    }

    @Test
    void eligibleTargetsRequireLiquidityResultEvenIfHistoriesAreMissing() {
        StockCandidateEvaluationRequest criteria = request("005930");

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.INCOMPLETE, List.of(eligibility(criteria, eligible("005930"))),
                List.of(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("Eligible targets require a liquidity result.");
    }

    @Test
    void rejectsLiquidityThatIncludesExcludedTargetsOrOmitsEligibleTargets() {
        StockCandidateEvaluationRequest criteria = request("069500", "005930", "000660");
        List<StockEligibilityResult> eligibility = List.of(eligibility(criteria, excluded("069500")),
                eligibility(criteria, eligible("005930")), eligibility(criteria, eligible("000660")));
        List<DailyTradingValueSelectionEvaluationRequest> wrongRequests = List.of(
                criteria.liquidityRequest(), criteria.liquidityRequestFor(List.of("005930")));

        for (DailyTradingValueSelectionEvaluationRequest wrongRequest : wrongRequests) {
            DailyTradingValueSelectionEvaluationResult wrong = liquidityService().evaluate(wrongRequest, List.of());
            assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                    StockCandidateEvaluationStatus.INCOMPLETE, eligibility, List.of(), wrong))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Liquidity request must match eligible targets and criteria.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"minimum", "limit", "venue", "window", "date"})
    void rejectsLiquidityResultThatChangesAnyOriginalCriterion(String changedField) {
        StockCandidateEvaluationRequest criteria = request("005930");
        DailyTradingValueSelectionEvaluationRequest original = criteria.liquidityRequest();
        DailyTradingValueSelectionEvaluationRequest changed = new DailyTradingValueSelectionEvaluationRequest(
                original.targetSymbols(), changedField.equals("date") ? DATE.plusDays(1) : DATE,
                switch (changedField) {
                    case "date" -> List.of(DATE.minusDays(1), DATE, DATE.plusDays(1));
                    case "window" -> List.of(DATE.minusDays(1), DATE);
                    default -> DATES;
                },
                changedField.equals("venue") ? TradingVenueScope.KRX : original.expectedVenueScope(),
                changedField.equals("minimum") ? 101L : original.minimumAverageTradingValueKrw(),
                changedField.equals("limit") ? 1 : original.maxCandidateCount());
        DailyTradingValueSelectionEvaluationResult wrong = liquidityService().evaluate(changed, List.of());

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.INCOMPLETE, List.of(eligibility(criteria, eligible("005930"))),
                List.of(), wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Liquidity request must match eligible targets and criteria.");
    }

    @Test
    void rejectsLiquidityResultThatReplacesEligibleInputHistory() {
        StockCandidateEvaluationRequest criteria = request("005930");
        DailyTradingValueSelectionEvaluationResult wrong = liquidityService().evaluate(
                criteria.liquidityRequest(), List.of(history("005930", 200L)));

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, List.of(eligibility(criteria, eligible("005930"))),
                List.of(history("005930", 100L)), wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Liquidity result must preserve eligible input histories.");
    }

    @Test
    void rejectsLiquidityResultThatDropsSuppliedOutOfWindowBarEvenIfCalculationWouldBeUnchanged() {
        StockCandidateEvaluationRequest criteria = request("005930");
        DailyPriceHistory original = history("005930", 100L);
        List<DailyPriceBar> bars = new ArrayList<>(original.bars());
        bars.add(new DailyPriceBar(DATE.plusDays(1), 100L, 110L, 90L, 100L, 10L,
                9999L, TradingVenueScope.INTEGRATED));
        DailyTradingValueSelectionEvaluationResult wrong = liquidityService().evaluate(
                criteria.liquidityRequest(), List.of(original));

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria,
                StockCandidateEvaluationStatus.COMPLETE, List.of(eligibility(criteria, eligible("005930"))),
                List.of(new DailyPriceHistory("005930", bars)), wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Liquidity result must preserve eligible input histories.");
    }

    @ParameterizedTest
    @EnumSource(StockCandidateEvaluationStatus.class)
    void statusMustMatchLiquidityCompletionState(StockCandidateEvaluationStatus status) {
        StockCandidateEvaluationRequest criteria = request("005930");
        List<DailyPriceHistory> histories = status == StockCandidateEvaluationStatus.COMPLETE
                ? List.of(history("005930", 100L)) : List.of();
        StockCandidateEvaluationResult result = service().evaluate(criteria, List.of(eligible("005930")), histories);
        StockCandidateEvaluationStatus wrongStatus = status == StockCandidateEvaluationStatus.COMPLETE
                ? StockCandidateEvaluationStatus.INCOMPLETE : StockCandidateEvaluationStatus.COMPLETE;

        assertThat(result.status()).isEqualTo(status);
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria, wrongStatus,
                result.eligibilityResults(), result.inputHistories(), result.liquidityResult()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("status must match liquidity evaluation status.");
        if (status == StockCandidateEvaluationStatus.INCOMPLETE) {
            assertThat(result.candidateSymbols()).isEmpty();
            assertThat(result.unverifiedSymbols()).containsExactly("005930");
        }
    }

    @Test
    void rejectsNullRequiredFieldsAndListEntries() {
        StockCandidateEvaluationRequest criteria = request("069500");
        List<StockEligibilityResult> eligibility = List.of(eligibility(criteria, excluded("069500")));

        assertThatThrownBy(() -> new StockCandidateEvaluationResult(
                null, StockCandidateEvaluationStatus.COMPLETE, eligibility, List.of(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria, null, eligibility, List.of(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("status must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.COMPLETE, null, List.of(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("eligibilityResults must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(
                criteria, StockCandidateEvaluationStatus.COMPLETE, eligibility, null, null))
                .isInstanceOf(NullPointerException.class).hasMessage("inputHistories must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria, StockCandidateEvaluationStatus.COMPLETE,
                Arrays.asList((StockEligibilityResult) null), List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StockCandidateEvaluationResult(criteria, StockCandidateEvaluationStatus.COMPLETE,
                eligibility, Arrays.asList((DailyPriceHistory) null), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void retainedRequestEligibilityInputsAndHistoriesReproduceFullResultUnderSamePolicies() {
        StockCandidateEvaluationResult original = service().evaluate(request("069500", "005930", "000660"),
                List.of(excluded("069500"), eligible("005930"), eligible("000660")),
                List.of(history("069500", 9999L), history("005930", 300L), history("000660", 100L)));

        StockCandidateEvaluationResult replayed = service().evaluate(original.request(),
                original.eligibilityResults().stream().map(StockEligibilityResult::input).toList(),
                original.inputHistories());

        assertThat(replayed).isEqualTo(original);
    }

    private StockEligibilityResult eligibility(StockCandidateEvaluationRequest request, StockEligibilityInput input) {
        return new StockEligibilityPolicy().evaluate(request.eligibilityRequest(), input);
    }

    private StockEligibilityInput excluded(String symbol) {
        return input(symbol, StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
    }
}
