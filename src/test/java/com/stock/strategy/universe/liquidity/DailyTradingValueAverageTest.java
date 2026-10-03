package com.stock.strategy.universe.liquidity;

import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueAverageTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES =
            List.of(FIRST_DATE, FIRST_DATE.plusDays(1), SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;

    @Test
    void preservesExactTotalAndDerivesDenominatorFromDates() {
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        DailyTradingValueAverage result = average("005930", total, TRADING_DATES);

        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.selectionAsOfDate()).isEqualTo(SELECTION_DATE);
        assertThat(result.tradingVenueScope()).isEqualTo(VENUE);
        assertThat(result.totalTradingValueKrw()).isEqualTo(total);
        assertThat(result.tradingDayCount()).isEqualTo(3);
    }

    @Test
    void allowsZeroTotal() {
        assertThat(average("005930", BigInteger.ZERO, TRADING_DATES).totalTradingValueKrw())
                .isEqualTo(BigInteger.ZERO);
    }

    @ParameterizedTest
    @CsvSource({
            "3000000, 1000000, true",
            "2999999, 1000000, false",
            "3000001, 1000000, true",
            "0, 1, false",
            "2, 1, false",
            "3, 1, true",
            "4, 1, true"
    })
    void comparesExactTotalAgainstMinimumWithoutRounding(
            long totalTradingValueKrw,
            long minimumAverageTradingValueKrw,
            boolean expected
    ) {
        DailyTradingValueAverage result = average(
                "005930", BigInteger.valueOf(totalTradingValueKrw), TRADING_DATES
        );

        assertThat(result.meetsMinimumAverageTradingValueKrw(minimumAverageTradingValueKrw))
                .isEqualTo(expected);
    }

    @Test
    void usesResultTradingDayCountAsDenominator() {
        DailyTradingValueAverage singleDay = average(
                "005930", BigInteger.valueOf(100L), List.of(SELECTION_DATE)
        );
        DailyTradingValueAverage threeDays = average(
                "005930", BigInteger.valueOf(100L), TRADING_DATES
        );

        assertThat(singleDay.meetsMinimumAverageTradingValueKrw(100L)).isTrue();
        assertThat(threeDays.meetsMinimumAverageTradingValueKrw(100L)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void comparesThresholdBeyondLongRangeExactly(int offset) {
        BigInteger minimumTotal = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(TRADING_DATES.size()));
        DailyTradingValueAverage result = average(
                "005930", minimumTotal.add(BigInteger.valueOf(offset)), TRADING_DATES
        );

        assertThat(result.meetsMinimumAverageTradingValueKrw(Long.MAX_VALUE))
                .isEqualTo(offset == 0);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectsNonPositiveMinimumAverageTradingValue(long minimumAverageTradingValueKrw) {
        DailyTradingValueAverage result = average("005930", BigInteger.ONE, TRADING_DATES);

        assertThatThrownBy(() -> result.meetsMinimumAverageTradingValueKrw(
                minimumAverageTradingValueKrw
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minimumAverageTradingValueKrw must be positive.");
    }

    @Test
    void copiesDatesAndExposesImmutableList() {
        List<LocalDate> callerDates = new ArrayList<>(TRADING_DATES);
        DailyTradingValueAverage result = average("005930", BigInteger.ONE, callerDates);

        callerDates.clear();

        assertThat(result.tradingDates()).containsExactlyElementsOf(TRADING_DATES);
        assertThat(result.tradingDayCount()).isEqualTo(3);
        assertThatThrownBy(() -> result.tradingDates().add(SELECTION_DATE.plusDays(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankSymbol(String symbol) {
        assertThatThrownBy(() -> average(symbol, BigInteger.ONE, TRADING_DATES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }

    @Test
    void rejectsNegativeTotal() {
        assertThatThrownBy(() -> average("005930", BigInteger.valueOf(-1L), TRADING_DATES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalTradingValueKrw must not be negative.");
    }

    @Test
    void rejectsNullTotal() {
        assertThatThrownBy(() -> average("005930", null, TRADING_DATES))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("totalTradingValueKrw must not be null.");
    }

    @Test
    void rejectsNullVenue() {
        assertThatThrownBy(() -> new DailyTradingValueAverage(
                "005930", SELECTION_DATE, TRADING_DATES, null, BigInteger.ONE
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tradingVenueScope must not be null.");
    }

    @Test
    void rejectsNullSelectionDate() {
        assertThatThrownBy(() -> new DailyTradingValueAverage(
                "005930", null, TRADING_DATES, VENUE, BigInteger.ONE
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selectionAsOfDate must not be null.");
    }

    @Test
    void rejectsNullTradingDates() {
        assertThatThrownBy(() -> average("005930", BigInteger.ONE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tradingDates must not be null.");
    }

    @Test
    void rejectsEmptyTradingDates() {
        assertThatThrownBy(() -> average("005930", BigInteger.ONE, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must not be empty.");
    }

    @Test
    void rejectsNullTradingDate() {
        assertThatThrownBy(() -> average(
                "005930", BigInteger.ONE, Arrays.asList(FIRST_DATE, null, SELECTION_DATE)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tradingDate must not be null.");
    }

    @Test
    void rejectsDuplicateTradingDates() {
        assertThatThrownBy(() -> average(
                "005930", BigInteger.ONE, List.of(FIRST_DATE, FIRST_DATE, SELECTION_DATE)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must be strictly increasing.");
    }

    @Test
    void rejectsReversedTradingDates() {
        assertThatThrownBy(() -> average(
                "005930", BigInteger.ONE, List.of(SELECTION_DATE, FIRST_DATE)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must be strictly increasing.");
    }

    @Test
    void rejectsFutureTradingDates() {
        assertThatThrownBy(() -> average(
                "005930", BigInteger.ONE, List.of(SELECTION_DATE, SELECTION_DATE.plusDays(1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must not be after selectionAsOfDate.");
    }

    @Test
    void rejectsDatesThatDoNotEndOnSelectionDate() {
        assertThatThrownBy(() -> average("005930", BigInteger.ONE, List.of(FIRST_DATE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must end on selectionAsOfDate.");
    }

    private DailyTradingValueAverage average(
            String symbol,
            BigInteger totalTradingValueKrw,
            List<LocalDate> tradingDates
    ) {
        return new DailyTradingValueAverage(
                symbol, SELECTION_DATE, tradingDates, VENUE, totalTradingValueKrw
        );
    }
}
