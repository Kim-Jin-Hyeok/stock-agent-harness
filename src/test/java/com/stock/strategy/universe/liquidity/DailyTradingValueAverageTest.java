package com.stock.strategy.universe.liquidity;

import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
