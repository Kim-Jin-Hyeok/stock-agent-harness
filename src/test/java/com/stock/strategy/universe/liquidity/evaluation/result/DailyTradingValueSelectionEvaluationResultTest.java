package com.stock.strategy.universe.liquidity.evaluation.result;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionResult;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionEvaluationResultTest {
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private final DailyTradingValueAverage samsung = average("005930", 300L);
    private final DailyTradingValueAverage hynix = average("000660", 200L);
    private final DailyTradingValueSelectionEvaluationRequest samsungRequest = request("005930");
    private final DailyTradingValueSelectionEvaluationRequest bothRequest = request("005930", "000660");

    @Test
    void completeResultPreservesEveryCalculatedAndSelectionResult() {
        DailyTradingValueSelectionResult first = selection(samsung, 1);
        DailyTradingValueSelectionResult second = selection(hynix, 2);

        DailyTradingValueSelectionEvaluationResult result = complete(
                bothRequest, List.of(hynix, samsung), List.of(first, second)
        );

        assertThat(result.status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.COMPLETE);
        assertThat(result.request()).isSameAs(bothRequest);
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
                bothRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                histories(averages), averages, unverified, selections
        );

        averages.clear();
        unverified.clear();
        selections.add(selection(samsung, 1));
        assertThat(result.calculatedAverages()).containsExactly(samsung);
        assertThat(result.request()).isSameAs(bothRequest);
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
        DailyTradingValueSelectionEvaluationResult result = complete(samsungRequest, averages, selections);

        averages.clear();
        selections.clear();

        assertThat(result.calculatedAverages()).containsExactly(samsung);
        assertThat(result.selectionResults()).containsExactly(selection(samsung, 1));
        assertThatThrownBy(result.selectionResults()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowsAllTargetsToBeUnverified() {
        DailyTradingValueSelectionEvaluationResult result = new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                List.of(), List.of(), List.of("005930"), List.of()
        );

        assertThat(result.calculatedAverages()).isEmpty();
        assertThat(result.request()).isSameAs(samsungRequest);
        assertThat(result.unverifiedSymbols()).containsExactly("005930");
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void rejectsOmittedRequestedTarget(DailyTradingValueSelectionEvaluationStatus status) {
        List<DailyTradingValueAverage> averages = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? List.of(samsung) : List.of();
        List<String> unverified = status == DailyTradingValueSelectionEvaluationStatus.INCOMPLETE
                ? List.of("005930") : List.of();
        List<DailyTradingValueSelectionResult> selections = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? List.of(selection(samsung, 1)) : List.of();

        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                bothRequest, status, histories(averages), averages, unverified, selections
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated and unverified symbols must exactly cover request targetSymbols.");
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void rejectsUnexpectedCalculatedTarget(DailyTradingValueSelectionEvaluationStatus status) {
        List<String> unverified = status == DailyTradingValueSelectionEvaluationStatus.INCOMPLETE
                ? List.of("005930") : List.of();
        List<DailyTradingValueSelectionResult> selections = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? List.of(selection(hynix, 1)) : List.of();

        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, status, histories(List.of(hynix)), List.of(hynix), unverified, selections
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated and unverified symbols must exactly cover request targetSymbols.");
    }

    @Test
    void rejectsUnexpectedUnverifiedTargetEvenWhenAllDataIsMissing() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                List.of(), List.of(), List.of("000660"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated and unverified symbols must exactly cover request targetSymbols.");
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void rejectsDifferentTargetSetEvenIfClassifiedCountMatches(DailyTradingValueSelectionEvaluationStatus status) {
        DailyTradingValueAverage naver = average("035420", 150L);
        List<DailyTradingValueAverage> averages = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? List.of(samsung, naver) : List.of(samsung);
        List<String> unverified = status == DailyTradingValueSelectionEvaluationStatus.INCOMPLETE
                ? List.of("035420") : List.of();
        List<DailyTradingValueSelectionResult> selections = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? List.of(selection(samsung, 1), selection(naver, 2)) : List.of();

        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                bothRequest, status, histories(averages), averages, unverified, selections
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated and unverified symbols must exactly cover request targetSymbols.");
    }

    @ParameterizedTest
    @MethodSource("mismatchedAverages")
    void rejectsConsistentCalculatedContextThatDoesNotMatchRequest(
            DailyTradingValueAverage wrongAverage, DailyTradingValueSelectionEvaluationStatus status
    ) {
        DailyTradingValueSelectionEvaluationRequest request = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? samsungRequest : bothRequest;
        List<String> unverified = status == DailyTradingValueSelectionEvaluationStatus.INCOMPLETE
                ? List.of("000660") : List.of();
        List<DailyTradingValueSelectionResult> selections = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? List.of(selection(wrongAverage, 1)) : List.of();

        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                request, status, histories(List.of(wrongAverage)), List.of(wrongAverage), unverified, selections
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated average must match request trading window and venue. symbol=005930");
    }

    @Test
    void rejectsDifferentTradingDatesEvenIfTheirCountAndSelectionDateMatch() {
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, List.of(SELECTION_DATE.minusDays(1), SELECTION_DATE),
                TradingVenueScope.INTEGRATED, 100L, 1
        );
        DailyTradingValueAverage differentDates = new DailyTradingValueAverage(
                "005930", SELECTION_DATE, List.of(SELECTION_DATE.minusDays(2), SELECTION_DATE),
                TradingVenueScope.INTEGRATED, BigInteger.valueOf(300L)
        );

        assertThatThrownBy(() -> complete(
                request, List.of(differentDates), List.of(selection(differentDates, 1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated average must match request trading window and venue. symbol=005930");
    }

    @Test
    void preservesRequestWhenCallerMutatesOriginalInputsAfterAllMissingResult() {
        List<String> symbols = new ArrayList<>(List.of("005930", "000660"));
        List<LocalDate> dates = new ArrayList<>(List.of(SELECTION_DATE));
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                symbols, SELECTION_DATE, dates, TradingVenueScope.NXT, 987L, 9
        );
        DailyTradingValueSelectionEvaluationResult result = new DailyTradingValueSelectionEvaluationResult(
                request, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                List.of(), List.of(), request.targetSymbols(), List.of()
        );

        symbols.clear();
        dates.clear();

        assertThat(result.request()).isSameAs(request);
        assertThat(result.request().targetSymbols()).containsExactly("000660", "005930");
        assertThat(result.request().requiredTradingDates()).containsExactly(SELECTION_DATE);
        assertThat(result.request().selectionAsOfDate()).isEqualTo(SELECTION_DATE);
        assertThat(result.request().expectedVenueScope()).isEqualTo(TradingVenueScope.NXT);
        assertThat(result.request().minimumAverageTradingValueKrw()).isEqualTo(987L);
        assertThat(result.request().maxCandidateCount()).isEqualTo(9);
        assertThatThrownBy(result.request().targetSymbols()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.request().requiredTradingDates()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsCompleteStatusWithUnverifiedSymbols() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                bothRequest, DailyTradingValueSelectionEvaluationStatus.COMPLETE,
                histories(List.of(samsung)), List.of(samsung),
                List.of("000660"), List.of(selection(samsung, 1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMPLETE status must not contain unverified symbols.");
    }

    @Test
    void rejectsCompleteStatusWithoutCalculatedTargets() {
        assertThatThrownBy(() -> complete(samsungRequest, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMPLETE status requires calculated averages.");
    }

    @Test
    void rejectsIncompleteStatusWithoutUnverifiedSymbols() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                histories(List.of(samsung)), List.of(samsung), List.of(), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INCOMPLETE status requires unverified symbols.");
    }

    @Test
    void rejectsPartialSelectionResultsForIncompleteStatus() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                bothRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                histories(List.of(samsung)), List.of(samsung),
                List.of("000660"), List.of(selection(samsung, 1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INCOMPLETE status must not contain selection results.");
    }

    @Test
    void rejectsSymbolOverlapBetweenCalculatedAndUnverified() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                histories(List.of(samsung)), List.of(samsung), List.of("005930"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated and unverified symbols must not overlap: 005930");
    }

    @Test
    void rejectsDuplicateCalculatedSymbol() {
        assertThatThrownBy(() -> complete(samsungRequest, List.of(samsung, average("005930", 301L)), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate calculated symbol: 005930");
    }

    @Test
    void rejectsDuplicateUnverifiedSymbol() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(), List.of(),
                List.of("005930", "005930"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate unverified symbol: 005930");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankUnverifiedSymbol(String symbol) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                List.of(), List.of(), List.of(symbol), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unverifiedSymbols must not contain blank symbol.");
    }

    @Test
    void rejectsIncompleteCoverageOfCalculatedAverages() {
        assertThatThrownBy(() -> complete(bothRequest, List.of(samsung, hynix), List.of(selection(samsung, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must cover every calculated average.");
    }

    @Test
    void rejectsSelectionResultForUnexpectedSymbol() {
        assertThatThrownBy(() -> complete(samsungRequest, List.of(samsung), List.of(selection(hynix, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must preserve calculated averages.");
    }

    @Test
    void rejectsChangedAverageInsideSelectionResult() {
        assertThatThrownBy(() -> complete(
                samsungRequest, List.of(samsung), List.of(selection(average("005930", 301L), 1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must preserve calculated averages.");
    }

    @Test
    void rejectsDuplicateSelectionResultSymbol() {
        assertThatThrownBy(() -> complete(
                bothRequest, List.of(samsung, hynix), List.of(selection(samsung, 1), selection(samsung, 2))
        ))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate selection result symbol: 005930");
    }

    @Test
    void rejectsNonConsecutiveResultRanks() {
        assertThatThrownBy(() -> complete(samsungRequest, List.of(samsung), List.of(selection(samsung, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionResults must have consecutive ranks starting at one.");
    }

    @Test
    void rejectsMixedSelectionDatesInCalculatedAverages() {
        DailyTradingValueAverage earlier = new DailyTradingValueAverage(
                "000660", SELECTION_DATE.minusDays(1), List.of(SELECTION_DATE.minusDays(1)),
                TradingVenueScope.INTEGRATED, BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> complete(bothRequest, List.of(samsung, earlier), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated average must match request trading window and venue. symbol=000660");
    }

    @Test
    void rejectsMixedTradingDateListsInCalculatedAverages() {
        DailyTradingValueAverage longer = new DailyTradingValueAverage(
                "000660", SELECTION_DATE, List.of(SELECTION_DATE.minusDays(1), SELECTION_DATE),
                TradingVenueScope.INTEGRATED, BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> complete(bothRequest, List.of(samsung, longer), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated average must match request trading window and venue. symbol=000660");
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void rejectsMixedVenuesInCalculatedAveragesEvenWhenIncomplete(TradingVenueScope venue) {
        DailyTradingValueAverage differentVenue = new DailyTradingValueAverage(
                "000660", SELECTION_DATE, List.of(SELECTION_DATE), venue, BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                request("005930", "000660", "035420"), DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                histories(List.of(samsung, differentVenue)),
                List.of(samsung, differentVenue),
                List.of("035420"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated average must match request trading window and venue. symbol=000660");
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> complete(null, List.of(samsung), List.of(selection(samsung, 1))))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
    }

    @Test
    void rejectsNullStatus() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, null, List.of(), List.of(), List.of(), List.of()
        ))
                .isInstanceOf(NullPointerException.class).hasMessage("status must not be null.");
    }

    @Test
    void rejectsNullCalculatedList() {
        assertThatThrownBy(() -> complete(samsungRequest, null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("calculatedAverages must not be null.");
    }

    @Test
    void rejectsNullUnverifiedList() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.COMPLETE,
                histories(List.of(samsung)), List.of(samsung), null, List.of(selection(samsung, 1))
        ))
                .isInstanceOf(NullPointerException.class).hasMessage("unverifiedSymbols must not be null.");
    }

    @Test
    void rejectsNullSelectionList() {
        assertThatThrownBy(() -> complete(samsungRequest, List.of(samsung), null))
                .isInstanceOf(NullPointerException.class).hasMessage("selectionResults must not be null.");
    }

    @Test
    void rejectsNullEntriesInEachResultList() {
        assertThatThrownBy(() -> complete(samsungRequest, Arrays.asList((DailyTradingValueAverage) null), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                List.of(), List.of(), Arrays.asList((String) null), List.of()
        ))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> complete(
                samsungRequest, List.of(samsung), Arrays.asList((DailyTradingValueSelectionResult) null)
        ))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void copiesAndSortsInputHistoriesWithoutChangingCallerCollections() {
        DailyPriceHistory hynixHistory = histories(List.of(hynix)).getFirst();
        List<DailyPriceBar> bars = new ArrayList<>(histories(List.of(samsung)).getFirst().bars());
        DailyPriceHistory samsungHistory = new DailyPriceHistory("005930", bars);
        List<DailyPriceHistory> inputs = new ArrayList<>(List.of(samsungHistory, hynixHistory));

        DailyTradingValueSelectionEvaluationResult result = new DailyTradingValueSelectionEvaluationResult(
                bothRequest, DailyTradingValueSelectionEvaluationStatus.COMPLETE, inputs,
                List.of(hynix, samsung), List.of(), List.of(selection(samsung, 1), selection(hynix, 2))
        );

        assertThat(inputs).containsExactly(samsungHistory, hynixHistory);
        inputs.clear();
        bars.clear();
        assertThat(result.inputHistories()).containsExactly(hynixHistory, samsungHistory);
        assertThat(result.inputHistories().getLast()).isSameAs(samsungHistory);
        assertThat(result.inputHistories().getLast().bars()).hasSize(1);
        assertThatThrownBy(result.inputHistories()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.inputHistories().getLast().bars()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesEmptyAndUnknownMetadataHistoriesWithoutFabricatingMissingHistory() {
        DailyPriceHistory empty = new DailyPriceHistory("000660", List.of());
        DailyPriceHistory unknown = new DailyPriceHistory("005930", List.of(
                new DailyPriceBar(SELECTION_DATE, 100L, 100L, 100L, 100L, 1L, 0L, null)
        ));
        DailyTradingValueSelectionEvaluationRequest request = request("000660", "005930", "035420");

        DailyTradingValueSelectionEvaluationResult result = new DailyTradingValueSelectionEvaluationResult(
                request, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE, List.of(unknown, empty),
                List.of(), request.targetSymbols(), List.of()
        );

        assertThat(result.inputHistories()).containsExactly(empty, unknown);
        assertThat(result.inputHistories().getFirst().bars()).isEmpty();
        assertThat(result.inputHistories().getLast().bars().getFirst().tradingValueKrw()).isZero();
        assertThat(result.inputHistories().getLast().bars().getFirst().tradingVenueScope()).isNull();
        assertThat(result.unverifiedSymbols()).containsExactly("000660", "005930", "035420");
    }

    @Test
    void rejectsNullInputHistoryList() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.COMPLETE, null,
                List.of(samsung), List.of(), List.of(selection(samsung, 1))
        ))
                .isInstanceOf(NullPointerException.class).hasMessage("inputHistories must not be null.");
    }

    @Test
    void rejectsNullInputHistoryEntry() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                Arrays.asList((DailyPriceHistory) null), List.of(), List.of("005930"), List.of()
        ))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {300L, 301L})
    void rejectsDuplicateInputHistorySymbol(long total) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.COMPLETE,
                histories(List.of(samsung, average("005930", total))),
                List.of(samsung), List.of(), List.of(selection(samsung, 1))
        ))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate input history symbol: 005930");
    }

    @Test
    void rejectsInputHistoryOutsideRequestedTargetsEvenWhenUnverified() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                samsungRequest, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                histories(List.of(hynix)), List.of(), List.of("005930"), List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input history symbol must belong to request targetSymbols: 000660");
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void rejectsCalculatedSymbolWithoutInputHistory(DailyTradingValueSelectionEvaluationStatus status) {
        DailyTradingValueSelectionEvaluationRequest request = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? samsungRequest : bothRequest;
        List<String> unverified = status == DailyTradingValueSelectionEvaluationStatus.INCOMPLETE
                ? List.of("000660") : List.of();
        List<DailyTradingValueSelectionResult> selections = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? List.of(selection(samsung, 1)) : List.of();

        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationResult(
                request, status, List.of(), List.of(samsung), unverified, selections
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Calculated symbol must have an input history: 005930");
    }

    private DailyTradingValueSelectionEvaluationResult complete(
            DailyTradingValueSelectionEvaluationRequest request,
            List<DailyTradingValueAverage> averages, List<DailyTradingValueSelectionResult> selections
    ) {
        return new DailyTradingValueSelectionEvaluationResult(
                request, DailyTradingValueSelectionEvaluationStatus.COMPLETE,
                histories(averages), averages, List.of(), selections
        );
    }

    private List<DailyPriceHistory> histories(List<DailyTradingValueAverage> averages) {
        if (averages == null) {
            return List.of();
        }
        return averages.stream().filter(average -> average != null)
                .map(average -> new DailyPriceHistory(average.symbol(), average.tradingDates().stream()
                        .map(date -> new DailyPriceBar(
                                date, 100L, 100L, 100L, 100L, 1L,
                                date.equals(average.selectionAsOfDate())
                                        ? average.totalTradingValueKrw().longValueExact() : 0L,
                                average.tradingVenueScope()
                        )).toList())).toList();
    }

    private static Stream<Arguments> mismatchedAverages() {
        List<DailyTradingValueAverage> averages = List.of(
                new DailyTradingValueAverage(
                        "005930", SELECTION_DATE.minusDays(1), List.of(SELECTION_DATE.minusDays(1)),
                        TradingVenueScope.INTEGRATED, BigInteger.valueOf(300L)
                ),
                new DailyTradingValueAverage(
                        "005930", SELECTION_DATE.plusDays(1), List.of(SELECTION_DATE.plusDays(1)),
                        TradingVenueScope.INTEGRATED, BigInteger.valueOf(300L)
                ),
                new DailyTradingValueAverage(
                        "005930", SELECTION_DATE, List.of(SELECTION_DATE.minusDays(1), SELECTION_DATE),
                        TradingVenueScope.INTEGRATED, BigInteger.valueOf(300L)
                ),
                new DailyTradingValueAverage(
                        "005930", SELECTION_DATE, List.of(SELECTION_DATE),
                        TradingVenueScope.KRX, BigInteger.valueOf(300L)
                ),
                new DailyTradingValueAverage(
                        "005930", SELECTION_DATE, List.of(SELECTION_DATE),
                        TradingVenueScope.NXT, BigInteger.valueOf(300L)
                )
        );
        return averages.stream().flatMap(average -> Arrays.stream(DailyTradingValueSelectionEvaluationStatus.values())
                .map(status -> Arguments.of(average, status)));
    }

    private DailyTradingValueSelectionEvaluationRequest request(String... symbols) {
        return new DailyTradingValueSelectionEvaluationRequest(
                List.of(symbols), SELECTION_DATE, List.of(SELECTION_DATE), TradingVenueScope.INTEGRATED, 100L, 2
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
