package com.stock.strategy.universe.liquidity.evaluation.request;

import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionEvaluationRequestTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES = List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void preservesExplicitCriteriaAndCanonicalTargetOrder(TradingVenueScope venue) {
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("035420", "005930", "000660"), SELECTION_DATE, TRADING_DATES, venue,
                Long.MAX_VALUE, Integer.MAX_VALUE
        );

        assertThat(request.targetSymbols()).containsExactly("000660", "005930", "035420");
        assertThat(request.selectionAsOfDate()).isEqualTo(SELECTION_DATE);
        assertThat(request.requiredTradingDates()).containsExactlyElementsOf(TRADING_DATES);
        assertThat(request.expectedVenueScope()).isEqualTo(venue);
        assertThat(request.minimumAverageTradingValueKrw()).isEqualTo(Long.MAX_VALUE);
        assertThat(request.maxCandidateCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void copiesCollectionsWithoutMutatingCallerAndExposesImmutableLists() {
        List<String> symbols = new ArrayList<>(List.of("005930", "000660"));
        List<LocalDate> dates = new ArrayList<>(TRADING_DATES);

        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                symbols, SELECTION_DATE, dates, TradingVenueScope.INTEGRATED, 100L, 2
        );

        assertThat(symbols).containsExactly("005930", "000660");
        assertThat(dates).containsExactlyElementsOf(TRADING_DATES);
        symbols.clear();
        dates.clear();
        assertThat(request.targetSymbols()).containsExactly("000660", "005930");
        assertThat(request.requiredTradingDates()).containsExactlyElementsOf(TRADING_DATES);
        assertThatThrownBy(request.targetSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(request.requiredTradingDates()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equivalentTargetOrdersProduceEqualRequests() {
        assertThat(request(List.of("005930", "000660")))
                .isEqualTo(request(List.of("000660", "005930")));
    }

    @Test
    void acceptsSingleRequiredDateAndPositiveMinimumCriteria() {
        DailyTradingValueSelectionEvaluationRequest request = new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, List.of(SELECTION_DATE), TradingVenueScope.KRX, 1L, 1
        );

        assertThat(request.requiredTradingDates()).containsExactly(SELECTION_DATE);
        assertThat(request.minimumAverageTradingValueKrw()).isEqualTo(1L);
        assertThat(request.maxCandidateCount()).isEqualTo(1);
    }

    @Test
    void rejectsNullTargetList() {
        assertThatThrownBy(() -> request(null))
                .isInstanceOf(NullPointerException.class).hasMessage("targetSymbols must not be null.");
    }

    @Test
    void rejectsEmptyTargets() {
        assertThatThrownBy(() -> request(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("targetSymbols must not be empty.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankTargetSymbol(String symbol) {
        assertThatThrownBy(() -> request(Arrays.asList(symbol)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetSymbols must not contain blank symbol.");
    }

    @Test
    void rejectsDuplicateTargetSymbolInsteadOfSilentlyRemovingIt() {
        assertThatThrownBy(() -> request(List.of("005930", "005930")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate target symbol: 005930");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectsNonpositiveMinimum(long minimum) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, TRADING_DATES, TradingVenueScope.INTEGRATED, minimum, 1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minimumAverageTradingValueKrw must be positive.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonpositiveLimit(int limit) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, TRADING_DATES, TradingVenueScope.INTEGRATED, 100L, limit
        ))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("maxCandidateCount must be positive.");
    }

    @Test
    void rejectsNullSelectionDate() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), null, TRADING_DATES, TradingVenueScope.INTEGRATED, 100L, 1
        ))
                .isInstanceOf(NullPointerException.class).hasMessage("selectionAsOfDate must not be null.");
    }

    @Test
    void rejectsNullVenue() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, TRADING_DATES, null, 100L, 1
        ))
                .isInstanceOf(NullPointerException.class).hasMessage("expectedVenueScope must not be null.");
    }

    @ParameterizedTest
    @MethodSource("invalidTradingDates")
    void reusesExistingTradingDateValidation(
            List<LocalDate> dates, Class<? extends Throwable> exceptionType, String message
    ) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRequest(
                List.of("005930"), SELECTION_DATE, dates, TradingVenueScope.INTEGRATED, 100L, 1
        ))
                .isInstanceOf(exceptionType).hasMessage(message);
    }

    private static Stream<Arguments> invalidTradingDates() {
        return Stream.of(
                Arguments.of(null, NullPointerException.class, "tradingDates must not be null."),
                Arguments.of(List.of(), IllegalArgumentException.class, "tradingDates must not be empty."),
                Arguments.of(Arrays.asList(FIRST_DATE, null, SELECTION_DATE), NullPointerException.class,
                        "tradingDate must not be null."),
                Arguments.of(List.of(FIRST_DATE, FIRST_DATE, SELECTION_DATE), IllegalArgumentException.class,
                        "tradingDates must be strictly increasing."),
                Arguments.of(List.of(SECOND_DATE, FIRST_DATE, SELECTION_DATE), IllegalArgumentException.class,
                        "tradingDates must be strictly increasing."),
                Arguments.of(List.of(FIRST_DATE, SELECTION_DATE, SELECTION_DATE.plusDays(1)),
                        IllegalArgumentException.class, "tradingDates must not be after selectionAsOfDate."),
                Arguments.of(List.of(FIRST_DATE, SECOND_DATE), IllegalArgumentException.class,
                        "tradingDates must end on selectionAsOfDate.")
        );
    }

    private DailyTradingValueSelectionEvaluationRequest request(List<String> symbols) {
        return new DailyTradingValueSelectionEvaluationRequest(
                symbols, SELECTION_DATE, TRADING_DATES, TradingVenueScope.INTEGRATED, 100L, 2
        );
    }
}
