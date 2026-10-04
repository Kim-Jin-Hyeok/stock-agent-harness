package com.stock.strategy.universe.candidate.evaluation.query;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.candidate.evaluation.StockCandidateEvaluationService;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.result.StockEligibilityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATES;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.input;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StockCandidateEvaluationQueryServiceTest {
    private final DailyPriceHistoryQueryService historyQueryService = mock(DailyPriceHistoryQueryService.class);
    private final StockCandidateEvaluationService evaluationService = spy(StockCandidateEvaluationFixture.service());
    private final StockCandidateEvaluationQueryService queryService =
            new StockCandidateEvaluationQueryService(historyQueryService, evaluationService);

    @Test
    void queriesAllTargetsInExactRangeBeforeDelegatingEligibilityAndLiquidityToExistingEvaluator() {
        StockCandidateEvaluationRequest request = request(100L, 1, "005930", "069500", "000660");
        List<StockEligibilityInput> inputs = List.of(etf("069500"), eligible("005930"), eligible("000660"));
        DailyPriceHistory hynix = history("000660", 100L);
        DailyPriceHistory samsung = history("005930", 300L);
        DailyPriceHistory etf = history("069500", 9_000L);
        stub(hynix);
        stub(samsung);
        stub(etf);
        List<DailyPriceHistory> histories = List.of(hynix, samsung, etf);
        StockCandidateEvaluationResult expected = StockCandidateEvaluationFixture.service()
                .evaluate(request, inputs, histories);

        StockCandidateEvaluationResult result = queryService.evaluate(request, inputs);

        assertThat(result).isEqualTo(expected);
        assertThat(result.request()).isSameAs(request);
        assertThat(result.candidateSymbols()).containsExactly("005930");
        assertThat(result.inputHistories()).containsExactlyElementsOf(histories);
        assertThat(result.eligibilityResults()).extracting(value -> value.status())
                .containsExactly(StockEligibilityStatus.ELIGIBLE, StockEligibilityStatus.ELIGIBLE,
                        StockEligibilityStatus.INELIGIBLE);
        var order = inOrder(historyQueryService, evaluationService);
        order.verify(historyQueryService).getDailyPriceHistory(historyRequest("000660"));
        order.verify(historyQueryService).getDailyPriceHistory(historyRequest("005930"));
        order.verify(historyQueryService).getDailyPriceHistory(historyRequest("069500"));
        order.verify(evaluationService).evaluate(request, inputs, histories);
        verifyNoMoreInteractions(historyQueryService, evaluationService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesMissingOrUnverifiedEligibilityWithoutSelectingFromKnownTargets(boolean missing) {
        List<StockEligibilityInput> inputs = missing ? List.of(eligible("005930")) : List.of(eligible("005930"),
                input("000660", StockSecurityType.COMMON_STOCK, StockEligibilityEvidenceStatus.UNVERIFIED));
        DailyPriceHistory hynix = history("000660", 100L);
        DailyPriceHistory samsung = history("005930", 300L);
        stub(hynix);
        stub(samsung);

        StockCandidateEvaluationResult result = queryService.evaluate(request("005930", "000660"), inputs);

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.candidateSymbols()).isEmpty();
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.liquidityResult()).isNull();
        assertThat(result.eligibilityResults()).hasSize(2);
        assertThat(result.inputHistories()).containsExactly(hynix, samsung);
        verify(historyQueryService).getDailyPriceHistory(historyRequest("000660"));
        verify(historyQueryService).getDailyPriceHistory(historyRequest("005930"));
        verifyNoMoreInteractions(historyQueryService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesEmptyHistoryAndLetsEvaluatorDistinguishIneligibleFromMissingEligibleTarget(boolean excluded) {
        DailyPriceHistory samsung = history("005930", 100L);
        DailyPriceHistory empty = new DailyPriceHistory("069500", List.of());
        stub(samsung);
        stub(empty);
        List<StockEligibilityInput> inputs = List.of(eligible("005930"),
                excluded ? etf("069500") : eligible("069500"));

        StockCandidateEvaluationResult result = queryService.evaluate(request("005930", "069500"), inputs);

        assertThat(result.inputHistories()).containsExactly(samsung, empty);
        assertThat(result.status()).isEqualTo(excluded
                ? StockCandidateEvaluationStatus.COMPLETE : StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.candidateSymbols()).isEqualTo(excluded ? List.of("005930") : List.of());
        assertThat(result.unverifiedSymbols()).isEqualTo(excluded ? List.of() : List.of("069500"));
        verify(historyQueryService).getDailyPriceHistory(historyRequest("069500"));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void retainsUnknownStoredMetadataWithoutInventingZeroOrVenue(boolean missingValue, boolean missingVenue) {
        DailyPriceHistory history = history("005930", missingValue ? null : 100L,
                missingVenue ? null : TradingVenueScope.INTEGRATED);
        stub(history);

        StockCandidateEvaluationResult result = queryService.evaluate(request("005930"), List.of(eligible("005930")));

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.inputHistories()).containsExactly(history);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.candidateSymbols()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void propagatesKnownVenueConflictRatherThanReturningIncompleteEvaluation(TradingVenueScope venue) {
        stub(history("005930", 100L, venue));

        assertThatThrownBy(() -> queryService.evaluate(request("005930"), List.of(eligible("005930"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate=" + DATES.getFirst());
    }

    @Test
    void rejectsInvalidEligibilityInputsBeforeAnyQueryOrEvaluation() {
        StockCandidateEvaluationRequest request = request("005930");
        assertThatThrownBy(() -> queryService.evaluate(request, null))
                .isInstanceOf(NullPointerException.class).hasMessage("eligibilityInputs must not be null.");
        assertThatThrownBy(() -> queryService.evaluate(request, Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> queryService.evaluate(request, List.of(eligible("005930"), eligible("005930"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate eligibility input symbol: 005930");
        assertThatThrownBy(() -> queryService.evaluate(request, List.of(eligible("000660"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility input symbol must belong to targetSymbols: 000660");
        verifyNoInteractions(historyQueryService, evaluationService);
    }

    @Test
    void copiesEligibilityInputsBeforeQueriesCanChangeCallersList() {
        StockCandidateEvaluationRequest request = request("005930");
        StockEligibilityInput input = eligible("005930");
        List<StockEligibilityInput> inputs = new ArrayList<>(List.of(input));
        DailyPriceHistory history = history("005930", 100L);
        when(historyQueryService.getDailyPriceHistory(historyRequest("005930"))).thenAnswer(invocation -> {
            inputs.clear();
            return history;
        });
        doAnswer(invocation -> {
            List<StockEligibilityInput> copiedInputs = invocation.getArgument(1);
            List<DailyPriceHistory> copiedHistories = invocation.getArgument(2);
            assertThat(copiedInputs).containsExactly(input);
            assertThatThrownBy(copiedInputs::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(copiedHistories::clear).isInstanceOf(UnsupportedOperationException.class);
            return invocation.callRealMethod();
        }).when(evaluationService).evaluate(eq(request), anyList(), anyList());

        StockCandidateEvaluationResult result = queryService.evaluate(request, inputs);

        assertThat(inputs).isEmpty();
        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.COMPLETE);
        assertThat(result.eligibilityResults().getFirst().input()).isEqualTo(input);
        assertThat(result.inputHistories()).containsExactly(history);
    }

    @Test
    void propagatesQueryFailureWithoutEvaluatingPartiallyFetchedHistoriesOrQueryingLaterTargets() {
        stub(history("000660", 100L));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Database unavailable.");
        when(historyQueryService.getDailyPriceHistory(historyRequest("005930"))).thenThrow(failure);

        assertThatThrownBy(() -> queryService.evaluate(request("000660", "005930", "035420"),
                List.of(eligible("000660"), eligible("005930"), eligible("035420")))).isSameAs(failure);
        verify(historyQueryService).getDailyPriceHistory(historyRequest("000660"));
        verify(historyQueryService).getDailyPriceHistory(historyRequest("005930"));
        verifyNoMoreInteractions(historyQueryService);
        verifyNoInteractions(evaluationService);
    }

    @Test
    void rejectsNullHistoryResponseInsteadOfTreatingQueryFailureAsMissingData() {
        when(historyQueryService.getDailyPriceHistory(historyRequest("005930"))).thenReturn(null);

        assertThatThrownBy(() -> queryService.evaluate(request("005930"), List.of(eligible("005930"))))
                .isInstanceOf(NullPointerException.class).hasMessage("Stored daily price history must not be null.");
        verifyNoInteractions(evaluationService);
    }

    @Test
    void rejectsWrongHistorySymbolEvenWhenItBelongsToAnotherRequestedTarget() {
        when(historyQueryService.getDailyPriceHistory(historyRequest("000660"))).thenReturn(history("005930", 100L));

        assertThatThrownBy(() -> queryService.evaluate(request("000660", "005930"),
                List.of(eligible("000660"), eligible("005930"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored daily price history symbol must match request symbol. expected=000660, actual=005930");
        verify(historyQueryService).getDailyPriceHistory(historyRequest("000660"));
        verifyNoMoreInteractions(historyQueryService);
        verifyNoInteractions(evaluationService);
    }

    @Test
    void propagatesEvaluationFailureAfterQueryingAllTargets() {
        StockCandidateEvaluationRequest request = request("005930");
        List<StockEligibilityInput> inputs = List.of(eligible("005930"));
        DailyPriceHistory history = history("005930", 100L);
        stub(history);
        IllegalStateException failure = new IllegalStateException("Evaluation failed.");
        doThrow(failure).when(evaluationService).evaluate(request, inputs, List.of(history));

        assertThatThrownBy(() -> queryService.evaluate(request, inputs)).isSameAs(failure);
    }

    @Test
    void rejectsNullEvaluationResultInsteadOfReturningSuccessOrDefaultResult() {
        stub(history("005930", 100L));
        doReturn(null).when(evaluationService).evaluate(any(), anyList(), anyList());

        assertThatThrownBy(() -> queryService.evaluate(request("005930"), List.of(eligible("005930"))))
                .isInstanceOf(NullPointerException.class).hasMessage("Candidate evaluation must not return null.");
    }

    @Test
    void returnsExistingResultWithoutRebuildingIt() {
        StockCandidateEvaluationRequest request = request("005930");
        List<StockEligibilityInput> inputs = List.of(eligible("005930"));
        DailyPriceHistory history = history("005930", 100L);
        stub(history);
        StockCandidateEvaluationResult expected = StockCandidateEvaluationFixture.service()
                .evaluate(request, inputs, List.of(history));
        doReturn(expected).when(evaluationService).evaluate(request, inputs, List.of(history));

        assertThat(queryService.evaluate(request, inputs)).isSameAs(expected);
    }

    @Test
    void rejectsNullRequestAndDependencies() {
        assertThatThrownBy(() -> queryService.evaluate(null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationQueryService(null, evaluationService))
                .isInstanceOf(NullPointerException.class).hasMessage("historyQueryService must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationQueryService(historyQueryService, null))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationService must not be null.");
        verifyNoInteractions(historyQueryService, evaluationService);
    }

    private static StockEligibilityInput etf(String symbol) {
        return input(symbol, StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
    }

    private static DailyPriceHistoryRequest historyRequest(String symbol) {
        return new DailyPriceHistoryRequest(symbol, DATES.getFirst(), DATE);
    }

    private void stub(DailyPriceHistory history) {
        when(historyQueryService.getDailyPriceHistory(historyRequest(history.symbol()))).thenReturn(history);
    }
}
