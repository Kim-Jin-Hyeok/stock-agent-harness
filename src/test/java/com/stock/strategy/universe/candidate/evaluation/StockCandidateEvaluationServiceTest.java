package com.stock.strategy.universe.candidate.evaluation;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.eligibility.StockEligibilityPolicy;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.result.StockEligibilityReasonCode;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;
import com.stock.strategy.universe.eligibility.result.StockEligibilityStatus;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.input;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.liquidityService;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StockCandidateEvaluationServiceTest {
    private final StockEligibilityPolicy eligibilityPolicy = spy(new StockEligibilityPolicy());
    private final DailyTradingValueSelectionEvaluationService liquidity = spy(liquidityService());
    private final StockCandidateEvaluationService service = new StockCandidateEvaluationService(eligibilityPolicy, liquidity);

    @Test
    void filtersEtfBeforeRankingAndCandidateLimitWhilePreservingAllReasonsAndHistories() {
        StockCandidateEvaluationRequest criteria = request("005930", "000660", "005380", "035420", "069500");
        StockEligibilityInput etf = input("069500", StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
        List<StockEligibilityInput> inputs = List.of(etf, eligible("035420"), eligible("005930"),
                eligible("005380"), eligible("000660"));
        List<DailyPriceHistory> histories = List.of(history("069500", 9000L), history("005930", 300L),
                history("000660", 200L), history("005380", 150L), history("035420", 10L));

        StockCandidateEvaluationResult result = service.evaluate(criteria, inputs, histories);

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.request()).isSameAs(criteria);
        assertThat(result.candidateSymbols()).containsExactly("005930", "000660");
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.eligibilityResults()).extracting(value -> value.input().symbol())
                .containsExactlyElementsOf(criteria.targetSymbols());
        assertThat(result.eligibilityResults().getLast().input()).isSameAs(etf);
        assertThat(result.eligibilityResults().getLast().reasonCode())
                .isEqualTo(StockEligibilityReasonCode.UNSUPPORTED_SECURITY_TYPE);
        assertThat(result.inputHistories()).extracting(DailyPriceHistory::symbol)
                .containsExactlyElementsOf(criteria.targetSymbols());
        assertThat(result.liquidityResult().request().targetSymbols())
                .containsExactly("000660", "005380", "005930", "035420");
        assertThat(result.liquidityResult().selectionResults()).extracting(value -> value.average().symbol())
                .containsExactly("005930", "000660", "005380", "035420");
        assertThat(result.liquidityResult().selectionResults()).extracting(value -> value.status())
                .containsExactly(DailyTradingValueSelectionStatus.SELECTED, DailyTradingValueSelectionStatus.SELECTED,
                        DailyTradingValueSelectionStatus.CANDIDATE_LIMIT, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM);
        InOrder order = inOrder(eligibilityPolicy, liquidity);
        for (String symbol : criteria.targetSymbols()) {
            order.verify(eligibilityPolicy).evaluate(criteria.eligibilityRequest(),
                    inputs.stream().filter(value -> value.symbol().equals(symbol)).findFirst().orElseThrow());
        }
        order.verify(liquidity).evaluate(criteria.liquidityRequestFor(List.of("000660", "005380", "005930", "035420")),
                result.liquidityResult().inputHistories());
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @EnumSource(value = StockSecurityType.class, names = {"ETF", "ETN"})
    void excludedTargetsNeedNoHistoryAndCannotMakeEligibleEvaluationIncomplete(StockSecurityType type) {
        StockCandidateEvaluationResult result = service.evaluate(request("005930", "069500"),
                List.of(eligible("005930"), input("069500", type, StockEligibilityEvidenceStatus.AS_OF_VERIFIED)),
                List.of(history("005930", 100L)));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.candidateSymbols()).containsExactly("005930");
        assertThat(result.liquidityResult().request().targetSymbols()).containsExactly("005930");
        assertThat(result.eligibilityResults()).hasSize(2);
    }

    @Test
    void preservesExcludedHistoryButDoesNotPassItsVenueOrMissingValuesToLiquidityEvaluation() {
        DailyPriceHistory excluded = history("069500", null, TradingVenueScope.KRX);
        StockCandidateEvaluationResult result = service.evaluate(request("005930", "069500"),
                List.of(eligible("005930"), input("069500", StockSecurityType.ETF,
                        StockEligibilityEvidenceStatus.AS_OF_VERIFIED)),
                List.of(excluded, history("005930", 100L)));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.inputHistories()).contains(excluded);
        assertThat(result.liquidityResult().inputHistories()).extracting(DailyPriceHistory::symbol)
                .containsExactly("005930");
    }

    @Test
    void returnsCompleteEmptyCandidatesWithoutCallingLiquidityWhenAllTargetsAreIneligible() {
        StockCandidateEvaluationResult result = service.evaluate(request("069500", "000660"), List.of(
                input("069500", StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                input("000660", StockSecurityType.ETN, StockEligibilityEvidenceStatus.AS_OF_VERIFIED)), List.of());

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.liquidityResult()).isNull();
        assertThat(result.eligibilityResults()).hasSize(2)
                .allMatch(value -> value.status() == StockEligibilityStatus.INELIGIBLE);
        verifyNoInteractions(liquidity);
    }

    @ParameterizedTest
    @EnumSource(value = StockEligibilityEvidenceStatus.class, names = {"UNVERIFIED", "CURRENT_ONLY"})
    void doesNotSelectVerifiedSubsetWhenAnyEligibilityIsUnverified(StockEligibilityEvidenceStatus evidence) {
        DailyPriceHistory supplied = history("005930", 300L);
        StockCandidateEvaluationResult result = service.evaluate(request("000660", "005930"),
                List.of(eligible("005930"), input("000660", StockSecurityType.COMMON_STOCK, evidence)),
                List.of(supplied));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.inputHistories()).containsExactly(supplied);
        assertThat(result.liquidityResult()).isNull();
        assertThat(result.eligibilityResults()).hasSize(2);
        verify(eligibilityPolicy).evaluate(result.request().eligibilityRequest(), eligible("005930"));
        verifyNoInteractions(liquidity);
    }

    @Test
    void missingEligibilityRetainsTargetWithExplicitUnknownInputRatherThanDeletingIt() {
        StockCandidateEvaluationResult result = service.evaluate(request("000660", "005930"),
                List.of(eligible("005930")), List.of(history("000660", 9999L), history("005930", 100L)));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.eligibilityResults().getFirst().input().symbol()).isEqualTo("000660");
        assertThat(result.eligibilityResults().getFirst().input().asOfDate()).isNull();
        assertThat(result.eligibilityResults().getFirst().input().evidenceStatus())
                .isEqualTo(StockEligibilityEvidenceStatus.UNVERIFIED);
        assertThat(result.eligibilityResults().getFirst().reasonCode())
                .isEqualTo(StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED);
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.inputHistories()).hasSize(2);
        assertThat(result.candidateSymbols()).isEmpty();
        verifyNoInteractions(liquidity);
    }

    @Test
    void allMissingEligibilityStillClassifiesEveryRequestedTarget() {
        StockCandidateEvaluationRequest criteria = request("035420", "005930", "000660");
        StockCandidateEvaluationResult result = service.evaluate(criteria, List.of(), List.of());

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactlyElementsOf(criteria.targetSymbols());
        assertThat(result.eligibilityResults()).hasSize(3);
        assertThat(result.inputHistories()).isEmpty();
        verifyNoInteractions(liquidity);
    }

    @Test
    void eligibleMissingHistoryKeepsSuccessfulCalculationsButReturnsNoCandidates() {
        StockCandidateEvaluationResult result = service.evaluate(request("069500", "005930", "000660"),
                List.of(eligible("005930"), eligible("000660"), input("069500", StockSecurityType.ETF,
                        StockEligibilityEvidenceStatus.AS_OF_VERIFIED)),
                List.of(history("069500", 9000L), history("005930", 300L)));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.eligibilityResults()).hasSize(3);
        assertThat(result.inputHistories()).hasSize(2);
        assertThat(result.liquidityResult().calculatedAverages()).extracting(DailyTradingValueAverage::symbol)
                .containsExactly("005930");
        assertThat(result.liquidityResult().selectionResults()).isEmpty();
        assertThat(result.liquidityResult().request().targetSymbols()).containsExactly("000660", "005930");
    }

    @Test
    void eligibleMissingTradingValueIsUnverifiedNotBelowThresholdOrExcluded() {
        StockCandidateEvaluationResult result = service.evaluate(request("005930"), List.of(eligible("005930")),
                List.of(history("005930", null, TradingVenueScope.INTEGRATED)));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.eligibilityResults().getFirst().status()).isEqualTo(StockEligibilityStatus.ELIGIBLE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.candidateSymbols()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 99L})
    void completeNoCandidateResultDistinguishesKnownLiquidityBelowThreshold(long perDayValue) {
        StockCandidateEvaluationResult result = service.evaluate(request("005930"), List.of(eligible("005930")),
                List.of(history("005930", perDayValue)));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.liquidityResult().selectionResults().getFirst().status())
                .isEqualTo(DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM);
    }

    @ParameterizedTest
    @CsvSource({"0,1,2", "0,2,1", "1,0,2", "1,2,0", "2,0,1", "2,1,0"})
    void inputPermutationDoesNotChangeFullResultOrTieAtCandidateLimit(int first, int second, int third) {
        StockCandidateEvaluationRequest criteria = request(100L, 1, "069500", "005930", "000660");
        List<StockEligibilityInput> inputs = List.of(eligible("005930"), eligible("000660"),
                input("069500", StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED));
        List<DailyPriceHistory> histories = List.of(history("005930", 100L), history("000660", 100L), history("069500", 9000L));
        StockCandidateEvaluationResult original = service.evaluate(criteria, inputs, histories);
        StockCandidateEvaluationResult reordered = service.evaluate(criteria,
                List.of(inputs.get(first), inputs.get(second), inputs.get(third)),
                List.of(histories.get(third), histories.get(second), histories.get(first)));

        assertThat(reordered).isEqualTo(original);
        assertThat(reordered.candidateSymbols()).containsExactly("000660");
    }

    @Test
    void futureBarIsRetainedAsInputButCannotChangeRequiredWindowCalculation() {
        DailyPriceHistory original = history("005930", 100L);
        List<DailyPriceBar> bars = new ArrayList<>(original.bars());
        bars.add(new DailyPriceBar(DATE.plusDays(1), 100L, 110L, 90L, 100L, 10L,
                Long.MAX_VALUE, TradingVenueScope.INTEGRATED));
        DailyPriceHistory withFuture = new DailyPriceHistory("005930", bars);

        StockCandidateEvaluationResult result = service.evaluate(request("005930"), List.of(eligible("005930")), List.of(withFuture));

        assertThat(result.candidateSymbols()).containsExactly("005930");
        assertThat(result.liquidityResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(300L));
        assertThat(result.inputHistories()).containsExactly(withFuture);
    }

    @Test
    void leavesCallerListsUnchangedAndResultIndependentOfLaterMutation() {
        List<StockEligibilityInput> inputs = new ArrayList<>(List.of(eligible("005930"), eligible("000660")));
        List<DailyPriceHistory> histories = new ArrayList<>(List.of(history("005930", 200L), history("000660", 100L)));
        StockCandidateEvaluationResult result = service.evaluate(request("000660", "005930"), inputs, histories);

        assertThat(inputs).extracting(StockEligibilityInput::symbol).containsExactly("005930", "000660");
        assertThat(histories).extracting(DailyPriceHistory::symbol).containsExactly("005930", "000660");
        inputs.clear();
        histories.clear();
        assertThat(result.eligibilityResults()).hasSize(2);
        assertThat(result.inputHistories()).hasSize(2);
        assertThatThrownBy(result.candidateSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void policyAndServiceDoNotRetainEarlierCandidateLimitOrOutcome() {
        List<StockEligibilityInput> inputs = List.of(eligible("005930"), eligible("000660"));
        List<DailyPriceHistory> histories = List.of(history("005930", 200L), history("000660", 100L));

        assertThat(service.evaluate(request(100L, 1, "005930", "000660"), inputs, histories).candidateSymbols())
                .containsExactly("005930");
        assertThat(service.evaluate(request(201L, 2, "005930", "000660"), inputs, histories).candidateSymbols()).isEmpty();
        assertThat(service.evaluate(request(100L, 2, "005930", "000660"), inputs, histories).candidateSymbols())
                .containsExactly("005930", "000660");
    }

    @Test
    void rejectsDuplicateEligibilityBeforeAnyPolicyCall() {
        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(eligible("005930"), eligible("005930")), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate eligibility input symbol: 005930");
        verifyNoInteractions(eligibilityPolicy, liquidity);
    }

    @Test
    void rejectsDuplicateHistoryBeforeEligibilityEvenWhenAllTargetsWouldBeExcluded() {
        assertThatThrownBy(() -> service.evaluate(request("069500"), List.of(input("069500", StockSecurityType.ETF,
                        StockEligibilityEvidenceStatus.AS_OF_VERIFIED)),
                List.of(history("069500", 100L), history("069500", 200L))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate history symbol: 069500");
        verifyNoInteractions(eligibilityPolicy, liquidity);
    }

    @Test
    void rejectsOffTargetInputsRatherThanSilentlyFilteringThem() {
        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(eligible("000660")), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility input symbol must belong to targetSymbols: 000660");
        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(), List.of(history("000660", 100L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("History symbol must belong to targetSymbols: 000660");
        verifyNoInteractions(eligibilityPolicy, liquidity);
    }

    @Test
    void rejectsNullRequestListsAndEntriesInsteadOfTreatingThemAsMissingTargets() {
        StockCandidateEvaluationRequest criteria = request("005930");
        assertThatThrownBy(() -> service.evaluate(null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        assertThatThrownBy(() -> service.evaluate(criteria, null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("eligibilityInputs must not be null.");
        assertThatThrownBy(() -> service.evaluate(criteria, List.of(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("histories must not be null.");
        assertThatThrownBy(() -> service.evaluate(criteria, Arrays.asList((StockEligibilityInput) null), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.evaluate(criteria, List.of(), Arrays.asList((DailyPriceHistory) null)))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(eligibilityPolicy, liquidity);
    }

    @Test
    void propagatesEligibilityFailureAndDoesNotStartLiquidity() {
        IllegalStateException failure = new IllegalStateException("synthetic eligibility failure");
        doThrow(failure).when(eligibilityPolicy).evaluate(any(), any());

        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(eligible("005930")), List.of()))
                .isSameAs(failure);
        verifyNoInteractions(liquidity);
    }

    @Test
    void propagatesLiquidityFailureRatherThanConvertingItToIncomplete() {
        IllegalStateException failure = new IllegalStateException("synthetic liquidity failure");
        doThrow(failure).when(liquidity).evaluate(any(), anyList());

        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(eligible("005930")), List.of()))
                .isSameAs(failure);
    }

    @Test
    void eligibleKnownVenueConflictRemainsAnErrorRatherThanPartialSuccess() {
        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(eligible("005930")),
                List.of(history("005930", 100L, TradingVenueScope.KRX))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullEligibilityResponse() {
        doReturn(null).when(eligibilityPolicy).evaluate(any(), any());
        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(eligible("005930")), List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Eligibility evaluation must not return null.");
        verifyNoInteractions(liquidity);
    }

    @Test
    void rejectsEligibilityResponseThatChangesInput() {
        StockCandidateEvaluationRequest criteria = request("005930");
        StockEligibilityInput changed = input("005930", StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
        StockEligibilityResult wrong = new StockEligibilityPolicy().evaluate(criteria.eligibilityRequest(), changed);
        doReturn(wrong).when(eligibilityPolicy).evaluate(any(), any());

        assertThatThrownBy(() -> service.evaluate(criteria, List.of(eligible("005930")), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility evaluation must preserve request and input.");
        verifyNoInteractions(liquidity);
    }

    @Test
    void rejectsNullLiquidityResponse() {
        doReturn(null).when(liquidity).evaluate(any(), anyList());
        assertThatThrownBy(() -> service.evaluate(request("005930"), List.of(eligible("005930")), List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Liquidity evaluation must not return null.");
    }

    @Test
    void requiresBothExistingDependencies() {
        assertThatThrownBy(() -> new StockCandidateEvaluationService(null, liquidity))
                .isInstanceOf(NullPointerException.class).hasMessage("eligibilityPolicy must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationService(eligibilityPolicy, null))
                .isInstanceOf(NullPointerException.class).hasMessage("liquidityEvaluationService must not be null.");
    }
}
