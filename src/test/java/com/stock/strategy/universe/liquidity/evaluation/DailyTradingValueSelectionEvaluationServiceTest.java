package com.stock.strategy.universe.liquidity.evaluation;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionResult;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DailyTradingValueSelectionEvaluationServiceTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES =
            List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;

    private final DailyTradingValueAverageCalculator calculator =
            spy(new DailyTradingValueAverageCalculator());
    private final DailyTradingValueSelectionPolicy selectionPolicy =
            spy(new DailyTradingValueSelectionPolicy(new DailyTradingValueRankingPolicy()));
    private final DailyTradingValueSelectionEvaluationService service =
            new DailyTradingValueSelectionEvaluationService(calculator, selectionPolicy);

    @Test
    void calculatesAllTargetsAndDelegatesCompleteSetToExistingSelectionPolicy() {
        List<String> symbols = List.of("035420", "005930", "005380", "000660");
        List<DailyPriceHistory> histories = List.of(
                completeHistory("005930", 300L), completeHistory("000660", 200L),
                completeHistory("035420", 150L), completeHistory("005380", 10L)
        );

        DailyTradingValueSelectionEvaluationResult result = evaluate(symbols, histories);

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.request()).isEqualTo(request(symbols));
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.inputHistories()).containsExactly(
                histories.get(1), histories.get(3), histories.get(0), histories.get(2)
        );
        assertThat(result.calculatedAverages()).extracting(DailyTradingValueAverage::symbol)
                .containsExactly("000660", "005380", "005930", "035420");
        assertThat(result.selectionResults()).containsExactly(
                selection("005930", 900L, 1, DailyTradingValueSelectionStatus.SELECTED),
                selection("000660", 600L, 2, DailyTradingValueSelectionStatus.SELECTED),
                selection("035420", 450L, 3, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT),
                selection("005380", 30L, 4, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
        verify(calculator, times(4)).calculate(
                any(DailyPriceHistory.class), eq(SELECTION_DATE), eq(TRADING_DATES), eq(VENUE)
        );
        verify(selectionPolicy).select(result.calculatedAverages(), 100L, 2);
    }

    @Test
    void preservesMissingHistorySymbolAndSuccessfulCalculationsWithoutSelecting() {
        DailyPriceHistory samsung = completeHistory("005930", 300L);
        DailyPriceHistory naver = completeHistory("035420", 150L);

        DailyTradingValueSelectionEvaluationResult result = evaluate(
                List.of("035420", "000660", "005930"), List.of(naver, samsung)
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.inputHistories()).containsExactly(samsung, naver);
        assertThat(result.calculatedAverages()).containsExactly(
                average("005930", 900L), average("035420", 450L)
        );
        assertThat(result.selectionResults()).isEmpty();
        verify(calculator).calculate(samsung, SELECTION_DATE, TRADING_DATES, VENUE);
        verify(calculator).calculate(naver, SELECTION_DATE, TRADING_DATES, VENUE);
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void retainsEveryTargetWhenNoHistoriesWereProvided() {
        DailyTradingValueSelectionEvaluationResult result = evaluate(
                List.of("005930", "000660"), List.of()
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.inputHistories()).isEmpty();
        assertThat(result.unverifiedSymbols()).containsExactly("000660", "005930");
        assertThat(result.selectionResults()).isEmpty();
        verifyNoInteractions(calculator, selectionPolicy);
    }

    @Test
    void preservesNondefaultRequestEvenWhenEveryTargetIsUnverified() {
        LocalDate selectionDate = LocalDate.of(2026, 9, 25);
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930", "000660"), selectionDate,
                List.of(selectionDate.minusDays(1), selectionDate), TradingVenueScope.KRX, 98_765L, 7
        );

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request, List.of());

        assertThat(result.request()).isSameAs(request);
        assertThat(result.request().targetSymbols()).containsExactly("000660", "005930");
        assertThat(result.request().selectionAsOfDate()).isEqualTo(selectionDate);
        assertThat(result.request().requiredTradingDates())
                .containsExactly(selectionDate.minusDays(1), selectionDate);
        assertThat(result.request().expectedVenueScope()).isEqualTo(TradingVenueScope.KRX);
        assertThat(result.request().minimumAverageTradingValueKrw()).isEqualTo(98_765L);
        assertThat(result.request().maxCandidateCount()).isEqualTo(7);
        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactlyElementsOf(request.targetSymbols());
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        verifyNoInteractions(calculator, selectionPolicy);
    }

    @ParameterizedTest
    @CsvSource({
            "100, 1, SELECTED, CANDIDATE_LIMIT",
            "100, 2, SELECTED, SELECTED",
            "101, 2, LIQUIDITY_BELOW_MINIMUM, LIQUIDITY_BELOW_MINIMUM"
    })
    void preservesAndAppliesExplicitSelectionCriteria(
            long minimum, int limit,
            DailyTradingValueSelectionStatus firstStatus, DailyTradingValueSelectionStatus secondStatus
    ) {
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930", "000660"), SELECTION_DATE, TRADING_DATES, VENUE, minimum, limit
        );

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request, List.of(
                completeHistory("005930", 100L), completeHistory("000660", 100L)
        ));

        assertThat(result.request()).isSameAs(request);
        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.selectionResults()).containsExactly(
                selection("000660", 300L, 1, firstStatus), selection("005930", 300L, 2, secondStatus)
        );
        verify(selectionPolicy).select(result.calculatedAverages(), minimum, limit);
    }

    @Test
    void treatsEmptyHistoryAsUnverifiedRatherThanAsZero() {
        DailyPriceHistory empty = history("005930");

        DailyTradingValueSelectionEvaluationResult result = evaluate(List.of("005930"), List.of(empty));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.inputHistories()).containsExactly(empty);
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        verify(calculator).calculate(empty, SELECTION_DATE, TRADING_DATES, VENUE);
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void retainsMissingRequiredDateEvenIfOlderAndFutureBarsCouldFillTheCount() {
        DailyPriceHistory incomplete = history(
                "000660", bar(FIRST_DATE, 100L), bar(SELECTION_DATE, 100L),
                bar(LocalDate.of(2026, 9, 18), 900L), bar(SELECTION_DATE.plusDays(1), 900L)
        );

        DailyTradingValueSelectionEvaluationResult result = evaluate(
                List.of("000660", "005930"), List.of(incomplete, completeHistory("005930", 300L))
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 900L));
        assertThat(result.selectionResults()).isEmpty();
        verifyNoInteractions(selectionPolicy);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void preservesUnknownTradingValueOrVenueAsUnverified(boolean missingValue, boolean missingVenue) {
        DailyPriceHistory incomplete = history(
                "005930", bar(FIRST_DATE, 100L),
                bar(SECOND_DATE, missingValue ? null : 100L, missingVenue ? null : VENUE),
                bar(SELECTION_DATE, 100L)
        );

        DailyTradingValueSelectionEvaluationResult result = evaluate(List.of("005930"), List.of(incomplete));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
        assertThat(result.inputHistories()).containsExactly(incomplete);
        DailyPriceBar preserved = result.inputHistories().getFirst().bars().get(1);
        assertThat(preserved.tradingValueKrw()).isEqualTo(missingValue ? null : 100L);
        assertThat(preserved.tradingVenueScope()).isEqualTo(missingVenue ? null : VENUE);
        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.selectionResults()).isEmpty();
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void keepsKnownZeroAsCompleteCalculationAndBelowMinimumSelectionResult() {
        DailyTradingValueSelectionEvaluationResult result = evaluate(
                List.of("005930", "000660"),
                List.of(completeHistory("005930", 0L), completeHistory("000660", 100L))
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.inputHistories()).containsExactly(
                completeHistory("000660", 100L), completeHistory("005930", 0L)
        );
        assertThat(result.calculatedAverages()).containsExactly(
                average("000660", 300L), average("005930", 0L)
        );
        assertThat(result.selectionResults()).containsExactly(
                selection("000660", 300L, 1, DailyTradingValueSelectionStatus.SELECTED),
                selection("005930", 0L, 2, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
    }

    @Test
    void canCompleteWithNoSelectedCandidateWhenAllKnownValuesAreZero() {
        DailyTradingValueSelectionEvaluationResult result = evaluate(
                List.of("005930"), List.of(completeHistory("005930", 0L))
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.selectionResults()).containsExactly(
                selection("005930", 0L, 1, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
    }

    @Test
    void preservesZeroCalculationWhenAnotherTargetIsUnverified() {
        DailyTradingValueSelectionEvaluationResult result = evaluate(
                List.of("005930", "000660"), List.of(completeHistory("005930", 0L))
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 0L));
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.selectionResults()).isEmpty();
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void ignoresOlderAndFutureBarMetadataOutsideTheRequiredWindow() {
        DailyPriceHistory input = history(
                "005930", bar(FIRST_DATE, 100L), bar(SECOND_DATE, 100L), bar(SELECTION_DATE, 100L),
                bar(LocalDate.of(2026, 9, 18), 900L, TradingVenueScope.KRX),
                bar(SELECTION_DATE.plusDays(1), null, null)
        );

        DailyTradingValueSelectionEvaluationResult result = evaluate(List.of("005930"), List.of(input));

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages()).containsExactly(average("005930", 300L));
        assertThat(result.inputHistories()).containsExactly(input);
        assertThat(result.inputHistories().getFirst().bars()).hasSize(5);
        assertThat(result.selectionResults()).containsExactly(
                selection("005930", 300L, 1, DailyTradingValueSelectionStatus.SELECTED)
        );
    }

    @Test
    void handlesTotalsAndThresholdProductsBeyondLongRangeExactly() {
        DailyPriceHistory below = history(
                "000660", bar(FIRST_DATE, Long.MAX_VALUE), bar(SECOND_DATE, Long.MAX_VALUE),
                bar(SELECTION_DATE, Long.MAX_VALUE - 1L)
        );
        BigInteger thresholdTotal = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3L));

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("000660", "005930"), SELECTION_DATE, TRADING_DATES, VENUE, Long.MAX_VALUE, 1
                ),
                List.of(below, completeHistory("005930", Long.MAX_VALUE))
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.selectionResults()).containsExactly(
                new DailyTradingValueSelectionResult(
                        average("005930", thresholdTotal), 1, DailyTradingValueSelectionStatus.SELECTED
                ),
                new DailyTradingValueSelectionResult(
                        average("000660", thresholdTotal.subtract(BigInteger.ONE)), 2,
                        DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM
                )
        );
    }

    @ParameterizedTest
    @CsvSource({"0, 1, 2", "0, 2, 1", "1, 0, 2", "1, 2, 0", "2, 0, 1", "2, 1, 0"})
    void completeEvaluationDoesNotDependOnTargetOrHistoryOrder(int first, int second, int third) {
        List<String> symbols = List.of("005930", "000660", "035420");
        List<DailyPriceHistory> histories = List.of(
                completeHistory("005930", 100L), completeHistory("000660", 100L),
                completeHistory("035420", 100L)
        );
        DailyTradingValueSelectionEvaluationResult reference = evaluate(symbols, histories);

        DailyTradingValueSelectionEvaluationResult reordered = evaluate(
                List.of(symbols.get(first), symbols.get(second), symbols.get(third)),
                List.of(histories.get(third), histories.get(first), histories.get(second))
        );

        assertThat(reordered).isEqualTo(reference);
        assertThat(reordered.selectionResults()).extracting(result -> result.average().symbol())
                .containsExactly("000660", "005930", "035420");
    }

    @Test
    void incompleteEvaluationAlsoHasStableCalculatedAndUnverifiedOrder() {
        DailyPriceHistory samsung = completeHistory("005930", 300L);
        DailyPriceHistory naver = completeHistory("035420", 100L);
        DailyTradingValueSelectionEvaluationResult reference = evaluate(
                List.of("035420", "005930", "000660", "005380"), List.of(samsung, naver)
        );

        DailyTradingValueSelectionEvaluationResult reordered = evaluate(
                List.of("005380", "000660", "005930", "035420"), List.of(naver, samsung)
        );

        assertThat(reordered).isEqualTo(reference);
        assertThat(reordered.unverifiedSymbols()).containsExactly("000660", "005380");
        assertThat(reordered.calculatedAverages()).extracting(DailyTradingValueAverage::symbol)
                .containsExactly("005930", "035420");
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void doesNotRetainPreviousMissingSymbolsOrCalculationsBetweenCalls() {
        List<String> symbols = List.of("005930", "000660");
        DailyTradingValueSelectionEvaluationResult incomplete = evaluate(
                symbols, List.of(completeHistory("005930", 100L))
        );
        DailyTradingValueSelectionEvaluationResult complete = evaluate(
                symbols, List.of(completeHistory("000660", 100L), completeHistory("005930", 100L))
        );
        DailyTradingValueSelectionEvaluationResult allMissing = evaluate(symbols, List.of());

        assertThat(incomplete.unverifiedSymbols()).containsExactly("000660");
        assertThat(complete.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(complete.unverifiedSymbols()).isEmpty();
        assertThat(allMissing.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(allMissing.calculatedAverages()).isEmpty();
        assertThat(allMissing.unverifiedSymbols()).containsExactly("000660", "005930");
        verify(selectionPolicy).select(anyList(), eq(100L), eq(2));
    }

    @Test
    void leavesCallerCollectionsUnchangedAndPreservesImmutableResults() {
        List<String> symbols = new ArrayList<>(List.of("005930", "000660"));
        List<DailyPriceHistory> histories = new ArrayList<>(List.of(
                completeHistory("005930", 200L), completeHistory("000660", 100L)
        ));
        List<LocalDate> dates = new ArrayList<>(TRADING_DATES);
        List<DailyPriceHistory> originalHistories = List.copyOf(histories);
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                symbols, SELECTION_DATE, dates, VENUE, 100L, 2
        );

        DailyTradingValueSelectionEvaluationResult result = service.evaluate(request, histories);

        assertThat(symbols).containsExactly("005930", "000660");
        assertThat(histories).containsExactlyElementsOf(originalHistories);
        assertThat(dates).containsExactlyElementsOf(TRADING_DATES);
        symbols.clear();
        histories.clear();
        dates.clear();
        assertThat(result.request()).isSameAs(request);
        assertThat(result.request().targetSymbols()).containsExactly("000660", "005930");
        assertThat(result.request().requiredTradingDates()).containsExactlyElementsOf(TRADING_DATES);
        assertThat(result.inputHistories()).containsExactly(originalHistories.get(1), originalHistories.get(0));
        assertThat(result.calculatedAverages()).containsExactly(average("000660", 300L), average("005930", 600L));
        assertThat(result.selectionResults()).hasSize(2);
        assertThatThrownBy(result.calculatedAverages()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.selectionResults()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.unverifiedSymbols().add("035420"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.request().targetSymbols()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.request().requiredTradingDates()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.inputHistories()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETE", "MISSING_HISTORY", "EMPTY_HISTORY", "MISSING_METADATA", "ALL_MISSING"})
    void reproducesEvaluationFromPreservedRequestAndInputs(String scenario) {
        List<DailyPriceHistory> histories = switch (scenario) {
            case "COMPLETE" -> List.of(
                    completeHistory("005930", 300L), completeHistory("000660", 200L),
                    completeHistory("035420", 150L), completeHistory("005380", 0L)
            );
            case "MISSING_HISTORY" -> List.of(completeHistory("005930", 300L));
            case "EMPTY_HISTORY" -> List.of(history("005930"));
            case "MISSING_METADATA" -> List.of(history(
                    "005930", bar(FIRST_DATE, 100L), bar(SECOND_DATE, null, null), bar(SELECTION_DATE, 100L)
            ));
            case "ALL_MISSING" -> List.of();
            default -> throw new IllegalArgumentException("Unknown test scenario: " + scenario);
        };
        DailyTradingValueSelectionEvaluationResult original = evaluate(
                List.of("035420", "000660", "005930", "005380"), histories
        );

        DailyTradingValueSelectionEvaluationResult replayed = service.evaluate(
                original.request(), original.inputHistories()
        );

        assertThat(replayed).isEqualTo(original);
        assertThat(original.inputHistories()).containsExactlyInAnyOrderElementsOf(histories);
        assertThat(original.status()).isEqualTo(scenario.equals("COMPLETE")
                ? DailyTradingValueSelectionEvaluationStatus.COMPLETE
                : DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
    }

    @Test
    void preservesIndexedInputsEvenIfCallerListChangesDuringCalculation() {
        DailyPriceHistory samsung = completeHistory("005930", 100L);
        DailyPriceHistory hynix = completeHistory("000660", 200L);
        List<DailyPriceHistory> inputs = new ArrayList<>(List.of(samsung, hynix));
        doAnswer(invocation -> {
            inputs.clear();
            inputs.add(completeHistory("005930", 900L));
            return invocation.callRealMethod();
        }).when(calculator).calculate(hynix, SELECTION_DATE, TRADING_DATES, VENUE);

        DailyTradingValueSelectionEvaluationResult result = evaluate(List.of("005930", "000660"), inputs);

        assertThat(result.inputHistories()).containsExactly(hynix, samsung);
        assertThat(result.calculatedAverages()).containsExactly(average("000660", 600L), average("005930", 300L));
        assertThat(inputs).containsExactly(completeHistory("005930", 900L));
    }

    @Test
    void distinguishesDifferentDailyInputsWithIdenticalCalculatedTotals() {
        DailyPriceHistory uniform = completeHistory("005930", 100L);
        DailyPriceHistory varied = history(
                "005930", bar(SELECTION_DATE, 150L), bar(FIRST_DATE, 50L), bar(SECOND_DATE, 100L)
        );

        DailyTradingValueSelectionEvaluationResult first = evaluate(List.of("005930"), List.of(uniform));
        DailyTradingValueSelectionEvaluationResult second = evaluate(List.of("005930"), List.of(varied));

        assertThat(first.calculatedAverages()).isEqualTo(second.calculatedAverages());
        assertThat(first.selectionResults()).isEqualTo(second.selectionResults());
        assertThat(first.inputHistories()).containsExactly(uniform);
        assertThat(second.inputHistories()).containsExactly(varied);
        assertThat(first).isNotEqualTo(second);
        assertThat(service.evaluate(second.request(), second.inputHistories())).isEqualTo(second);
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void knownVenueConflictIsNotHiddenByAnEarlierMissingTargetOrMissingBar(TradingVenueScope venue) {
        DailyPriceHistory conflicting = history("005930", bar(SELECTION_DATE, 0L, venue));

        assertThatThrownBy(() -> evaluate(List.of("000660", "005930"), List.of(conflicting)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate=" + SELECTION_DATE);
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void unexpectedCalculationFailurePropagatesInsteadOfBecomingUnverifiedData() {
        DailyPriceHistory history = completeHistory("005930", 100L);
        IllegalStateException failure = new IllegalStateException("Unexpected calculation failure.");
        doThrow(failure).when(calculator).calculate(history, SELECTION_DATE, TRADING_DATES, VENUE);

        assertThatThrownBy(() -> evaluate(List.of("005930"), List.of(history))).isSameAs(failure);
        verifyNoInteractions(selectionPolicy);
    }

    @Test
    void rejectsNullRequestBeforeCalculation() {
        assertThatThrownBy(() -> service.evaluate(null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        verifyNoInteractions(calculator, selectionPolicy);
    }

    @Test
    void rejectsNullHistoryListRatherThanTreatingItAsNoProvidedData() {
        assertThatThrownBy(() -> evaluate(List.of("005930"), null))
                .isInstanceOf(NullPointerException.class).hasMessage("histories must not be null.");
        verifyNoInteractions(calculator, selectionPolicy);
    }

    @Test
    void rejectsNullHistoryEntry() {
        assertThatThrownBy(() -> evaluate(List.of("005930"), Arrays.asList((DailyPriceHistory) null)))
                .isInstanceOf(NullPointerException.class).hasMessage("history must not be null.");
        verifyNoInteractions(calculator, selectionPolicy);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 100L})
    void rejectsDuplicateHistoryRatherThanChoosingOne(long duplicatedDailyValue) {
        assertThatThrownBy(() -> evaluate(List.of("005930"), List.of(
                completeHistory("005930", 100L), completeHistory("005930", duplicatedDailyValue)
        )))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate history symbol: 005930");
        verifyNoInteractions(calculator, selectionPolicy);
    }

    @Test
    void rejectsUnexpectedHistoryBeforeCalculatingAnyTarget() {
        assertThatThrownBy(() -> evaluate(List.of("005930"), List.of(
                completeHistory("005930", 100L), completeHistory("000660", 100L)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("History symbol must belong to targetSymbols: 000660");
        verifyNoInteractions(calculator, selectionPolicy);
    }

    @Test
    void rejectsNullCalculator() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationService(null, selectionPolicy))
                .isInstanceOf(NullPointerException.class).hasMessage("calculator must not be null.");
    }

    @Test
    void rejectsNullSelectionPolicy() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationService(calculator, null))
                .isInstanceOf(NullPointerException.class).hasMessage("selectionPolicy must not be null.");
    }

    private DailyTradingValueSelectionEvaluationResult evaluate(
            List<String> symbols, List<DailyPriceHistory> histories
    ) {
        return service.evaluate(request(symbols), histories);
    }

    private DailyTradingValueSelectionEvaluationRequest request(List<String> symbols) {
        return new DailyTradingValueSelectionEvaluationRequest(
                symbols, SELECTION_DATE, TRADING_DATES, VENUE, 100L, 2
        );
    }

    private DailyPriceHistory completeHistory(String symbol, long dailyValue) {
        return history(symbol, bar(FIRST_DATE, dailyValue), bar(SECOND_DATE, dailyValue), bar(SELECTION_DATE, dailyValue));
    }

    private DailyPriceHistory history(String symbol, DailyPriceBar... bars) {
        return new DailyPriceHistory(symbol, List.of(bars));
    }

    private DailyPriceBar bar(LocalDate date, Long value) {
        return bar(date, value, VENUE);
    }

    private DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope venue) {
        return new DailyPriceBar(date, 100L, 100L, 100L, 100L, 1L, value, venue);
    }

    private DailyTradingValueAverage average(String symbol, long total) {
        return average(symbol, BigInteger.valueOf(total));
    }

    private DailyTradingValueAverage average(String symbol, BigInteger total) {
        return new DailyTradingValueAverage(symbol, SELECTION_DATE, TRADING_DATES, VENUE, total);
    }

    private DailyTradingValueSelectionResult selection(
            String symbol, long total, int rank, DailyTradingValueSelectionStatus status
    ) {
        return new DailyTradingValueSelectionResult(average(symbol, total), rank, status);
    }
}
