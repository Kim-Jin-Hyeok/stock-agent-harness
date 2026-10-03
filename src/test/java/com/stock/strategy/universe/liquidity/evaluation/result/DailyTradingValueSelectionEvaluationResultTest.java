package com.stock.strategy.universe.liquidity.evaluation.result;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionResult;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionEvaluationResultTest {
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private final DailyTradingValueAverage samsung = average("005930", 300L);
    private final DailyTradingValueAverage hynix = average("000660", 200L);

    @Test
    void completeResultPreservesEveryCalculatedAndSelectionResult() {
        DailyTradingValueSelectionResult first = selection(samsung, 1);
        DailyTradingValueSelectionResult second = selection(hynix, 2);

        DailyTradingValueSelectionEvaluationResult result = complete(
                List.of(hynix, samsung), List.of(first, second)
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.calculatedAverages()).containsExactly(hynix, samsung);
        assertThat(result.unverifiedSymbols()).isEmpty();
        assertThat(result.selectionResults()).containsExactly(first, second);
        assertThat(result.selectionResults().getFirst()).isSameAs(first);
    }

    @Test
    void incompleteResultPreservesKnownValuesAndUnknownSymbolsWithoutSelections() {
        List<DailyTradingValueAverage> averages = new ArrayList<>(List.of(samsung));
        List<String> unverified = new ArrayList<>(List.of("000660"));
        List<DailyTradingValueSelectionResult> selections = new ArrayList<>();

        DailyTradingValueSelectionEvaluationResult result = new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, averages, unverified, selections
        );

        averages.clear();
        unverified.clear();
        selections.add(selection(samsung, 1));
        assertThat(result.calculatedAverages()).containsExactly(samsung);
        assertThat(result.unverifiedSymbols()).containsExactly("000660");
        assertThat(result.selectionResults()).isEmpty();
        assertThatThrownBy(result.calculatedAverages()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.unverifiedSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.selectionResults().add(selection(samsung, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void completeResultAlsoCopiesSelectionList() {
        List<DailyTradingValueAverage> averages = new ArrayList<>(List.of(samsung));
        List<DailyTradingValueSelectionResult> selections = new ArrayList<>(List.of(selection(samsung, 1)));
        DailyTradingValueSelectionEvaluationResult result = complete(averages, selections);

        averages.clear();
        selections.clear();

        assertThat(result.calculatedAverages()).containsExactly(samsung);
        assertThat(result.selectionResults()).containsExactly(selection(samsung, 1));
        assertThatThrownBy(result.selectionResults()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowsAllTargetsToBeUnverified() {
        DailyTradingValueSelectionEvaluationResult result = new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(), List.of("005930"), List.of()
        );

        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
    }

    @Test
    void rejectsCompleteStatusWithUnverifiedSymbols() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.COMPLETE, List.of(samsung),
                List.of("000660"), List.of(selection(samsung, 1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMPLETE status must not contain unverified symbols.");
    }

    @Test
    void rejectsCompleteStatusWithoutCalculatedTargets() {
        assertThatThrownBy(() -> complete(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMPLETE status requires calculated averages.");
    }

    @Test
    void rejectsIncompleteStatusWithoutUnverifiedSymbols() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(samsung), List.of(), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INCOMPLETE status requires unverified symbols.");
    }

    @Test
    void rejectsPartialSelectionResultsForIncompleteStatus() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(samsung),
                List.of("000660"), List.of(selection(samsung, 1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INCOMPLETE status must not contain selection results.");
    }

    @Test
    void rejectsSymbolOverlapBetweenCalculatedAndUnverified() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(samsung), List.of("005930"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated and unverified symbols must not overlap: 005930");
    }

    @Test
    void rejectsDuplicateCalculatedSymbol() {
        assertThatThrownBy(() -> complete(List.of(samsung, average("005930", 301L)), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate calculated symbol: 005930");
    }

    @Test
    void rejectsDuplicateUnverifiedSymbol() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(),
                List.of("005930", "005930"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate unverified symbol: 005930");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankUnverifiedSymbol(String symbol) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(), List.of(symbol), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unverifiedSymbols must not contain blank symbol.");
    }

    @Test
    void rejectsIncompleteCoverageOfCalculatedAverages() {
        assertThatThrownBy(() -> complete(List.of(samsung, hynix), List.of(selection(samsung, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must cover every calculated average.");
    }

    @Test
    void rejectsSelectionResultForUnexpectedSymbol() {
        assertThatThrownBy(() -> complete(List.of(samsung), List.of(selection(hynix, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must preserve calculated averages.");
    }

    @Test
    void rejectsChangedAverageInsideSelectionResult() {
        assertThatThrownBy(() -> complete(List.of(samsung), List.of(selection(average("005930", 301L), 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must preserve calculated averages.");
    }

    @Test
    void rejectsDuplicateSelectionResultSymbol() {
        assertThatThrownBy(() -> complete(
                List.of(samsung, hynix), List.of(selection(samsung, 1), selection(samsung, 2))
        ))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate selection result symbol: 005930");
    }

    @Test
    void rejectsNonConsecutiveResultRanks() {
        assertThatThrownBy(() -> complete(List.of(samsung), List.of(selection(samsung, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must have consecutive ranks starting at one.");
    }

    @Test
    void rejectsMixedSelectionDatesInCalculatedAverages() {
        DailyTradingValueAverage earlier = new DailyTradingValueAverage(
                "000660", SELECTION_DATE.minusDays(1), List.of(SELECTION_DATE.minusDays(1)),
                TradingVenueScope.INTEGRATED, BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> complete(List.of(samsung, earlier), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated averages must share the same trading window and venue.");
    }

    @Test
    void rejectsMixedTradingDateListsInCalculatedAverages() {
        DailyTradingValueAverage longer = new DailyTradingValueAverage(
                "000660", SELECTION_DATE, List.of(SELECTION_DATE.minusDays(1), SELECTION_DATE),
                TradingVenueScope.INTEGRATED, BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> complete(List.of(samsung, longer), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated averages must share the same trading window and venue.");
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void rejectsMixedVenuesInCalculatedAveragesEvenWhenIncomplete(TradingVenueScope venue) {
        DailyTradingValueAverage differentVenue = new DailyTradingValueAverage(
                "000660", SELECTION_DATE, List.of(SELECTION_DATE), venue, BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(samsung, differentVenue),
                List.of("035420"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated averages must share the same trading window and venue.");
    }

    @Test
    void rejectsNullStatus() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(null, List.of(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("status must not be null.");
    }

    @Test
    void rejectsNullCalculatedList() {
        assertThatThrownBy(() -> complete(null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("calculatedAverages must not be null.");
    }

    @Test
    void rejectsNullUnverifiedList() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.COMPLETE, List.of(samsung), null, List.of(selection(samsung, 1))
        ))
                .isInstanceOf(NullPointerException.class).hasMessage("unverifiedSymbols must not be null.");
    }

    @Test
    void rejectsNullSelectionList() {
        assertThatThrownBy(() -> complete(List.of(samsung), null))
                .isInstanceOf(NullPointerException.class).hasMessage("selectionResults must not be null.");
    }

    @Test
    void rejectsNullEntriesInEachResultList() {
        assertThatThrownBy(() -> complete(Arrays.asList((DailyTradingValueAverage) null), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(), Arrays.asList((String) null), List.of()
        ))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> complete(List.of(samsung), Arrays.asList((DailyTradingValueSelectionResult) null)))
                .isInstanceOf(NullPointerException.class);
    }

    private DailyTradingValueSelectionEvaluationResult complete(
            List<DailyTradingValueAverage> averages, List<DailyTradingValueSelectionResult> selections
    ) {
        return new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.COMPLETE, averages, List.of(), selections
        );
    }

    private DailyTradingValueAverage average(String symbol, long total) {
        return new DailyTradingValueAverage(
                symbol, SELECTION_DATE, List.of(SELECTION_DATE), TradingVenueScope.INTEGRATED, BigInteger.valueOf(total)
        );
    }

    private DailyTradingValueSelectionResult selection(DailyTradingValueAverage average, int rank) {
        return new DailyTradingValueSelectionResult(average, rank, DailyTradingValueSelectionStatus.SELECTED);
    }
}
