package com.stock.strategy.universe.liquidity.evaluation.query;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DailyTradingValueSelectionQueryServiceTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES = List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;

    private final DailyPriceHistoryQueryService historyQueryService = mock(DailyPriceHistoryQueryService.class);
    private final DailyTradingValueSelectionPolicy selectionPolicy =
            spy(new DailyTradingValueSelectionPolicy(new DailyTradingValueRankingPolicy()));
    private final DailyTradingValueSelectionEvaluationService evaluationService =
            spy(new DailyTradingValueSelectionEvaluationService(
                    new DailyTradingValueAverageCalculator(), selectionPolicy
            ));
    private final DailyTradingValueSelectionQueryService service =
            new DailyTradingValueSelectionQueryService(historyQueryService, evaluationService);

    @Test
    void queriesEveryTargetInExactRangeBeforeDelegatingToExistingEvaluator() {
        DailyTradingValueSelectionEvaluationRequest request = request("005930", "000660", "035420");
        DailyPriceHistory hynix = completeHistory("000660", 100L);
        DailyPriceHistory samsung = completeHistory("005930", 200L);
        DailyPriceHistory naver = completeHistory("035420", 150L);
        stub(hynix);
        stub(samsung);
        stub(naver);

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.request()).isSameAs(request);
        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages()).extracting(DailyTradingValueAverage::symbol)
                .containsExactly("000660", "005930", "035420");
        assertThat(result.selectionResults()).extracting(selection -> selection.average().symbol())
                .containsExactly("005930", "035420", "000660");
        assertThat(result.selectionResults()).extracting(selection -> selection.status())
                .containsExactly(DailyTradingValueSelectionStatus.SELECTED,
                        DailyTradingValueSelectionStatus.SELECTED, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT);
        var order = inOrder(historyQueryService, evaluationService);
        order.verify(historyQueryService).getDailyPriceHistory(historyRequest("000660"));
        order.verify(historyQueryService).getDailyPriceHistory(historyRequest("005930"));
        order.verify(historyQueryService).getDailyPriceHistory(historyRequest("035420"));
        order.verify(evaluationService).evaluate(request, List.of(hynix, samsung, naver));
        verifyNoMoreInteractions(historyQueryService, evaluationService);
    }

    @Test
    void passesEmptyHistoryWithoutDroppingTargetAndStillQueriesLaterTargets() {
        DailyTradingValueSelectionEvaluationRequest request = request("005930", "000660");
        DailyPriceHistory missing = history("000660");
        DailyPriceHistory known = completeHistory("005930", 100L);
        stub(missing);
        stub(known);

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.request()).isSameAs(request);
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 300L));
        assertThat(result.selectionResults()).isEmpty();
        verify(evaluationService).evaluate(request, List.of(missing, known));
        verify(historyQueryService).getDailyPriceHistory(historyRequest("005930"));
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void preservesAllTargetsAndCriteriaWhenAllStoredHistoriesAreEmpty() {
        DailyTradingValueSelectionEvaluationRequest request = request("005930", "000660");
        stub(history("005930"));
        stub(history("000660"));

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.request()).isSameAs(request);
        assertThat(result.unverifiedSymbols()).containsExactlyElementsOf(request.targetSymbols());
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void retainsMissingRequiredDateInsteadOfReducingDenominator() {
        stub(history("005930", bar(FIRST_DATE, 100L, VENUE), bar(SELECTION_DATE, 100L, VENUE)));

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request("005930"));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.calculatedAverages()).isEmpty();
        verify(historyQueryService).getDailyPriceHistory(historyRequest("005930"));
        verifyNoMoreInteractions(historyQueryService);
        verifyNoInteractions(selectionPolicy);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void preservesUnknownTradingValueOrVenue(boolean missingValue, boolean missingVenue) {
        stub(history("005930", bar(FIRST_DATE, 100L, VENUE),
                bar(SECOND_DATE, missingValue ? null : 100L, missingVenue ? null : VENUE),
                bar(SELECTION_DATE, 100L, VENUE)));

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request("005930"));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.selectionResults()).isEmpty();
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void preservesKnownZeroAsCalculatedBelowMinimumRatherThanUnverified() {
        stub(completeHistory("005930", 0L));

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request("005930"));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 0L));
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.selectionResults()).extracting(selection -> selection.status())
                .containsExactly(DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM);
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void propagatesKnownVenueConflictDespiteEarlierMissingTarget(TradingVenueScope venue) {
        stub(history("000660"));
        stub(history("005930", bar(SELECTION_DATE, 0L, venue)));

        assertThatThrownBy(() -> service.evaluate(request("000660", "005930")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate=" + SELECTION_DATE);
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void usesRequestedBoundsAndCriteriaWithoutDefaultDatesOrThresholds() {
        LocalDate selectionDate = LocalDate.of(2026, 9, 25);
        List<LocalDate> dates = List.of(SELECTION_DATE, selectionDate);
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), selectionDate, dates, TradingVenueScope.KRX, 999L, 1
        );
        DailyPriceHistory history = history("005930", bar(SELECTION_DATE, 999L, TradingVenueScope.KRX),
                bar(selectionDate, 999L, TradingVenueScope.KRX));
        DailyPriceHistoryRequest query = new DailyPriceHistoryRequest("005930", SELECTION_DATE, selectionDate);
        when(historyQueryService.getDailyPriceHistory(query)).thenReturn(history);

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request);

        assertThat(result.request()).isSameAs(request);
        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages().getFirst().tradingDates()).containsExactlyElementsOf(dates);
        assertThat(result.selectionResults().getFirst().status()).isEqualTo(DailyTradingValueSelectionStatus.SELECTED);
        verify(historyQueryService).getDailyPriceHistory(query);
        verifyNoMoreInteractions(historyQueryService);
        verify(selectionPolicy).select(result.calculatedAverages(), 999L, 1);
    }

    @Test
    void propagatesQueryFailureWithoutEvaluatingPartiallyFetchedHistories() {
        stub(completeHistory("000660", 100L));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Database unavailable.");
        when(historyQueryService.getDailyPriceHistory(historyRequest("005930"))).thenThrow(failure);

        assertThatThrownBy(() -> service.evaluate(request("000660", "005930", "035420"))).isSameAs(failure);

        verify(historyQueryService).getDailyPriceHistory(historyRequest("000660"));
        verify(historyQueryService).getDailyPriceHistory(historyRequest("005930"));
        verifyNoMoreInteractions(historyQueryService);
        verifyNoInteractions(evaluationService, selectionPolicy);
    }

    @Test
    void rejectsNullHistoryResponseRatherThanTreatingItAsMissingData() {
        when(historyQueryService.getDailyPriceHistory(historyRequest("005930"))).thenReturn(null);

        assertThatThrownBy(() -> service.evaluate(request("005930")))
                .isInstanceOf(NullPointerException.class).hasMessage("Stored daily price history must not be null.");
        verifyNoInteractions(evaluationService, selectionPolicy);
    }

    @Test
    void rejectsDifferentHistorySymbolEvenIfItIsAnotherRequestedTarget() {
        when(historyQueryService.getDailyPriceHistory(historyRequest("000660")))
                .thenReturn(completeHistory("005930", 100L));

        assertThatThrownBy(() -> service.evaluate(request("000660", "005930")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored daily price history symbol must match request symbol. expected=000660, actual=005930");
        verify(historyQueryService).getDailyPriceHistory(historyRequest("000660"));
        verifyNoMoreInteractions(historyQueryService);
        verifyNoInteractions(evaluationService, selectionPolicy);
    }

    @Test
    void propagatesUnexpectedEvaluationFailure() {
        DailyTradingValueSelectionEvaluationRequest request = request("005930");
        DailyPriceHistory history = completeHistory("005930", 100L);
        stub(history);
        IllegalStateException failure = new IllegalStateException("Evaluation failed.");
        doThrow(failure).when(evaluationService).evaluate(request, List.of(history));

        assertThatThrownBy(() -> service.evaluate(request)).isSameAs(failure);
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void returnsExistingEvaluatorsResultWithoutRebuildingIt() {
        DailyTradingValueSelectionEvaluationService evaluator = mock(DailyTradingValueSelectionEvaluationService.class);
        DailyTradingValueSelectionQueryService queryService =
                new DailyTradingValueSelectionQueryService(historyQueryService, evaluator);
        DailyTradingValueSelectionEvaluationRequest request = request("005930");
        DailyPriceHistory history = completeHistory("005930", 100L);
        stub(history);
        DailyTradingValueSelectionEvaluationResult expected = evaluationService.evaluate(request, List.of(history));
        when(evaluator.evaluate(request, List.of(history))).thenReturn(expected);

        assertThat(queryService.evaluate(request)).isSameAs(expected);
        verify(evaluator).evaluate(request, List.of(history));
    }

    @Test
    void doesNotModifyQueriedHistoriesOrRetainPreviousEvaluationState() {
        DailyTradingValueSelectionEvaluationRequest request = request("005930");
        List<DailyPriceBar> bars = new ArrayList<>(List.of(
                bar(FIRST_DATE, 100L, VENUE), bar(SECOND_DATE, 100L, VENUE), bar(SELECTION_DATE, 100L, VENUE)
        ));
        DailyPriceHistory complete = new DailyPriceHistory("005930", bars);
        DailyPriceHistory empty = history("005930");
        when(historyQueryService.getDailyPriceHistory(historyRequest("005930"))).thenReturn(complete, empty);

        DailyTradingValueSelectionEvaluationResult first = service.evaluate(request);
        DailyTradingValueSelectionEvaluationResult second = service.evaluate(request);

        assertThat(bars).containsExactlyElementsOf(complete.bars());
        assertThat(first.calculatedAverages()).containsExactly(average("005930", 300L));
        assertThat(second.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(second.calculatedAverages()).isEmpty();
        assertThat(second.unverifiedSymbols()).containsExactly("005930");
        assertThatThrownBy(first.calculatedAverages()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullRequestBeforeAnyQueryOrEvaluation() {
        assertThatThrownBy(() -> service.evaluate(null))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        verifyNoInteractions(historyQueryService, evaluationService, selectionPolicy);
    }

    @Test
    void rejectsNullHistoryQueryService() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionQueryService(null, evaluationService))
                .isInstanceOf(NullPointerException.class).hasMessage("historyQueryService must not be null.");
    }

    @Test
    void rejectsNullEvaluationService() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionQueryService(historyQueryService, null))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationService must not be null.");
    }

    private DailyTradingValueSelectionEvaluationRequest request(String... symbols) {
        return new DailyTradingValueSelectionEvaluationRequest(
                List.of(symbols), SELECTION_DATE, TRADING_DATES, VENUE, 100L, 2
        );
    }

    private DailyPriceHistoryRequest historyRequest(String symbol) {
        return new DailyPriceHistoryRequest(symbol, FIRST_DATE, SELECTION_DATE);
    }

    private void stub(DailyPriceHistory history) {
        when(historyQueryService.getDailyPriceHistory(historyRequest(history.symbol()))).thenReturn(history);
    }

    private DailyPriceHistory completeHistory(String symbol, long value) {
        return history(symbol, bar(FIRST_DATE, value, VENUE), bar(SECOND_DATE, value, VENUE),
                bar(SELECTION_DATE, value, VENUE));
    }

    private DailyPriceHistory history(String symbol, DailyPriceBar... bars) {
        return new DailyPriceHistory(symbol, List.of(bars));
    }

    private DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope venue) {
        return new DailyPriceBar(date, 100L, 100L, 100L, 100L, 1L, value, venue);
    }

    private DailyTradingValueAverage average(String symbol, long total) {
        return new DailyTradingValueAverage(symbol, SELECTION_DATE, TRADING_DATES, VENUE, BigInteger.valueOf(total));
    }
}
