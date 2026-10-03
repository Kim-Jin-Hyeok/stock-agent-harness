package com.stock.strategy.universe.liquidity;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueAverageCalculatorTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES =
            List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;

    private final DailyTradingValueAverageCalculator calculator =
            new DailyTradingValueAverageCalculator();

    @Test
    void preservesExactTotalAndDenominatorForRequiredTradingDates() {
        DailyTradingValueAverage result = calculate(history(
                bar(FIRST_DATE, 100L),
                bar(SECOND_DATE, 100L),
                bar(SELECTION_DATE, 102L)
        )).orElseThrow();

        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.selectionAsOfDate()).isEqualTo(SELECTION_DATE);
        assertThat(result.tradingDates()).containsExactlyElementsOf(TRADING_DATES);
        assertThat(result.tradingVenueScope()).isEqualTo(VENUE);
        assertThat(result.totalTradingValueKrw()).isEqualTo(BigInteger.valueOf(302L));
        assertThat(result.tradingDayCount()).isEqualTo(3);
    }

    @Test
    void includesKnownZeroWithoutReducingDenominator() {
        DailyTradingValueAverage result = calculate(history(
                bar(FIRST_DATE, 0L),
                bar(SECOND_DATE, 100L),
                bar(SELECTION_DATE, 200L)
        )).orElseThrow();

        assertThat(result.totalTradingValueKrw()).isEqualTo(BigInteger.valueOf(300L));
        assertThat(result.tradingDayCount()).isEqualTo(3);
    }

    @Test
    void allowsAllRequiredTradingValuesToBeZero() {
        DailyTradingValueAverage result = calculate(history(
                bar(FIRST_DATE, 0L), bar(SECOND_DATE, 0L), bar(SELECTION_DATE, 0L)
        )).orElseThrow();

        assertThat(result.totalTradingValueKrw()).isEqualTo(BigInteger.ZERO);
        assertThat(result.tradingDayCount()).isEqualTo(3);
    }

    @Test
    void sumsBeyondLongRangeWithoutOverflow() {
        DailyTradingValueAverage result = calculate(history(
                bar(FIRST_DATE, Long.MAX_VALUE),
                bar(SECOND_DATE, Long.MAX_VALUE),
                bar(SELECTION_DATE, Long.MAX_VALUE)
        )).orElseThrow();

        assertThat(result.totalTradingValueKrw()).isEqualTo(
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3L))
        );
    }

    @Test
    void returnsEmptyRatherThanFillingMissingDateWithOlderBar() {
        DailyPriceHistory history = history(
                bar(LocalDate.of(2026, 9, 18), 900L),
                bar(FIRST_DATE, 100L),
                bar(SELECTION_DATE, 300L)
        );

        assertThat(calculate(history)).isEmpty();
    }

    @Test
    void returnsEmptyRatherThanFillingMissingDateWithFutureBar() {
        DailyPriceHistory history = history(
                bar(FIRST_DATE, 100L),
                bar(SELECTION_DATE, 300L),
                bar(SELECTION_DATE.plusDays(1), 900L)
        );

        assertThat(calculate(history)).isEmpty();
    }

    @Test
    void returnsEmptyWhenHistoryIsEmpty() {
        assertThat(calculate(history())).isEmpty();
    }

    @Test
    void returnsEmptyWhenRequiredTradingValueIsUnknown() {
        assertThat(calculate(history(
                bar(FIRST_DATE, 100L), bar(SECOND_DATE, null), bar(SELECTION_DATE, 300L)
        ))).isEmpty();
    }

    @Test
    void returnsEmptyWhenRequiredVenueIsUnknown() {
        assertThat(calculate(history(
                bar(FIRST_DATE, 100L),
                bar(SECOND_DATE, 200L, null),
                bar(SELECTION_DATE, 300L)
        ))).isEmpty();
    }

    @Test
    void returnsEmptyForLegacyBarsWithoutMetadata() {
        DailyPriceBar legacyBar = new DailyPriceBar(
                SECOND_DATE, 100L, 100L, 100L, 100L, 1L
        );

        assertThat(calculate(history(
                bar(FIRST_DATE, 100L), legacyBar, bar(SELECTION_DATE, 300L)
        ))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void acceptsEachVenueWhenAllRequiredBarsMatch(TradingVenueScope venue) {
        DailyTradingValueAverage result = calculator.calculate(
                history(bar(FIRST_DATE, 100L, venue),
                        bar(SECOND_DATE, 200L, venue),
                        bar(SELECTION_DATE, 300L, venue)),
                SELECTION_DATE,
                TRADING_DATES,
                venue
        ).orElseThrow();

        assertThat(result.tradingVenueScope()).isEqualTo(venue);
        assertThat(result.totalTradingValueKrw()).isEqualTo(BigInteger.valueOf(600L));
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void rejectsKnownVenueConflict(TradingVenueScope otherVenue) {
        assertThatThrownBy(() -> calculate(history(
                bar(FIRST_DATE, 100L),
                bar(SECOND_DATE, 200L, otherVenue),
                bar(SELECTION_DATE, 300L)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate="
                        + SECOND_DATE);
    }

    @Test
    void missingDateDoesNotMaskKnownVenueConflict() {
        assertThatThrownBy(() -> calculate(history(
                bar(SECOND_DATE, 200L, TradingVenueScope.KRX),
                bar(SELECTION_DATE, 300L)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate="
                        + SECOND_DATE);
    }

    @Test
    void unknownMetadataDoesNotMaskKnownVenueConflict() {
        assertThatThrownBy(() -> calculate(history(
                bar(FIRST_DATE, null, null),
                bar(SECOND_DATE, 200L, TradingVenueScope.KRX),
                bar(SELECTION_DATE, 300L)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate="
                        + SECOND_DATE);
    }

    @Test
    void rejectsKnownVenueConflictEvenWhenTradingValueIsUnknown() {
        assertThatThrownBy(() -> calculate(history(
                bar(FIRST_DATE, 100L),
                bar(SECOND_DATE, null, TradingVenueScope.KRX),
                bar(SELECTION_DATE, 300L)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match expectedVenueScope. tradingDate="
                        + SECOND_DATE);
    }

    @Test
    void ignoresOlderAndFutureBarsOutsideRequiredDates() {
        DailyPriceHistory original = history(
                bar(FIRST_DATE, 100L), bar(SECOND_DATE, 200L), bar(SELECTION_DATE, 300L)
        );
        DailyPriceHistory extended = history(
                bar(LocalDate.of(2026, 9, 18), Long.MAX_VALUE, TradingVenueScope.KRX),
                bar(FIRST_DATE, 100L), bar(SECOND_DATE, 200L), bar(SELECTION_DATE, 300L),
                bar(SELECTION_DATE.plusDays(1), null, null),
                bar(SELECTION_DATE.plusDays(2), Long.MAX_VALUE, TradingVenueScope.NXT)
        );

        assertThat(calculate(extended)).isEqualTo(calculate(original));
    }

    @Test
    void producesSameResultRegardlessOfHistoryInputOrder() {
        DailyPriceHistory sorted = history(
                bar(FIRST_DATE, 100L), bar(SECOND_DATE, 200L), bar(SELECTION_DATE, 300L)
        );
        DailyPriceHistory unsorted = history(
                bar(SELECTION_DATE, 300L), bar(FIRST_DATE, 100L), bar(SECOND_DATE, 200L)
        );

        assertThat(calculate(unsorted)).isEqualTo(calculate(sorted));
    }

    @Test
    void preservesOtherSymbolWithoutInferringUniverseEligibility() {
        DailyPriceHistory history = new DailyPriceHistory("000660", List.of(
                bar(FIRST_DATE, 100L), bar(SECOND_DATE, 200L), bar(SELECTION_DATE, 300L)
        ));

        assertThat(calculate(history).orElseThrow().symbol()).isEqualTo("000660");
    }

    @Test
    void allowsSingleRequiredDate() {
        DailyTradingValueAverage result = calculator.calculate(
                history(bar(SELECTION_DATE, 100L)),
                SELECTION_DATE,
                List.of(SELECTION_DATE),
                VENUE
        ).orElseThrow();

        assertThat(result.totalTradingValueKrw()).isEqualTo(BigInteger.valueOf(100L));
        assertThat(result.tradingDayCount()).isEqualTo(1);
    }

    @Test
    void doesNotRequireCalendarDaysToBeConsecutive() {
        LocalDate friday = LocalDate.of(2026, 9, 18);
        DailyTradingValueAverage result = calculator.calculate(
                history(bar(friday, 100L), bar(FIRST_DATE, 200L)),
                FIRST_DATE,
                List.of(friday, FIRST_DATE),
                VENUE
        ).orElseThrow();

        assertThat(result.tradingDates()).containsExactly(friday, FIRST_DATE);
        assertThat(result.totalTradingValueKrw()).isEqualTo(BigInteger.valueOf(300L));
        assertThat(result.tradingDayCount()).isEqualTo(2);
    }

    @Test
    void rejectsNullHistory() {
        assertThatThrownBy(() -> calculator.calculate(null, SELECTION_DATE, TRADING_DATES, VENUE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("history must not be null.");
    }

    @Test
    void rejectsNullSelectionDate() {
        assertThatThrownBy(() -> calculator.calculate(history(), null, TRADING_DATES, VENUE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selectionAsOfDate must not be null.");
    }

    @Test
    void rejectsNullExpectedVenue() {
        assertThatThrownBy(() -> calculator.calculate(history(), SELECTION_DATE, TRADING_DATES, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expectedVenueScope must not be null.");
    }

    @Test
    void rejectsNullTradingDates() {
        assertThatThrownBy(() -> calculator.calculate(history(), SELECTION_DATE, null, VENUE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tradingDates must not be null.");
    }

    @Test
    void rejectsEmptyTradingDatesEvenWhenHistoryIsEmpty() {
        assertThatThrownBy(() -> calculator.calculate(history(), SELECTION_DATE, List.of(), VENUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must not be empty.");
    }

    @Test
    void rejectsNullElementInTradingDates() {
        assertThatThrownBy(() -> calculator.calculate(
                history(), SELECTION_DATE, Arrays.asList(FIRST_DATE, null, SELECTION_DATE), VENUE
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tradingDate must not be null.");
    }

    @Test
    void rejectsDuplicateTradingDates() {
        assertThatThrownBy(() -> calculator.calculate(
                history(), SELECTION_DATE, List.of(FIRST_DATE, FIRST_DATE, SELECTION_DATE), VENUE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must be strictly increasing.");
    }

    @Test
    void rejectsReversedTradingDatesRatherThanSilentlySortingThem() {
        assertThatThrownBy(() -> calculator.calculate(
                history(), SELECTION_DATE, List.of(SECOND_DATE, FIRST_DATE, SELECTION_DATE), VENUE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must be strictly increasing.");
    }

    @Test
    void rejectsRequiredTradingDateAfterSelectionDate() {
        assertThatThrownBy(() -> calculator.calculate(
                history(), SELECTION_DATE,
                List.of(FIRST_DATE, SELECTION_DATE, SELECTION_DATE.plusDays(1)), VENUE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must not be after selectionAsOfDate.");
    }

    @Test
    void rejectsTradingDatesThatDoNotEndOnSelectionDate() {
        assertThatThrownBy(() -> calculator.calculate(
                history(), SELECTION_DATE, List.of(FIRST_DATE, SECOND_DATE), VENUE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must end on selectionAsOfDate.");
    }

    @Test
    void preservesImmutableResultDatesWithoutMutatingCallerInputs() {
        List<LocalDate> callerDates = new ArrayList<>(TRADING_DATES);
        DailyPriceHistory history = history(
                bar(FIRST_DATE, 100L), bar(SECOND_DATE, 200L), bar(SELECTION_DATE, 300L)
        );
        List<DailyPriceBar> originalBars = history.bars();
        DailyTradingValueAverage result = calculator.calculate(
                history, SELECTION_DATE, callerDates, VENUE
        ).orElseThrow();

        assertThat(callerDates).containsExactlyElementsOf(TRADING_DATES);
        assertThat(history.bars()).isSameAs(originalBars);
        callerDates.clear();
        assertThat(result.tradingDates()).containsExactlyElementsOf(TRADING_DATES);
        assertThat(result.tradingDayCount()).isEqualTo(3);
        assertThatThrownBy(() -> result.tradingDates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Optional<DailyTradingValueAverage> calculate(DailyPriceHistory history) {
        return calculator.calculate(history, SELECTION_DATE, TRADING_DATES, VENUE);
    }

    private DailyPriceHistory history(DailyPriceBar... bars) {
        return new DailyPriceHistory("005930", List.of(bars));
    }

    private DailyPriceBar bar(LocalDate date, Long tradingValueKrw) {
        return bar(date, tradingValueKrw, VENUE);
    }

    private DailyPriceBar bar(LocalDate date, Long tradingValueKrw, TradingVenueScope venue) {
        return new DailyPriceBar(date, 100L, 100L, 100L, 100L, 1L, tradingValueKrw, venue);
    }
}
