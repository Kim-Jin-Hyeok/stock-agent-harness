package com.stock.strategy.universe.liquidity.ranking;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueRankingPolicyTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES =
            List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;

    private final DailyTradingValueRankingPolicy policy = new DailyTradingValueRankingPolicy();

    @Test
    void ranksByExactAverageTradingValueDescending() {
        DailyTradingValueAverage low = average("000660", 100L);
        DailyTradingValueAverage middle = average("035420", 200L);
        DailyTradingValueAverage high = average("005930", 300L);

        assertThat(policy.rank(List.of(low, high, middle)))
                .containsExactly(high, middle, low);
    }

    @Test
    void preservesDifferencesThatWouldBeLostByIntegerDivision() {
        DailyTradingValueAverage lower = average("000660", 300L);
        DailyTradingValueAverage higher = average("005930", 301L);

        assertThat(policy.rank(List.of(lower, higher))).containsExactly(higher, lower);
    }

    @Test
    void comparesTotalsBeyondLongRangeWithoutOverflow() {
        BigInteger lowerTotal = BigInteger.valueOf(Long.MAX_VALUE);
        DailyTradingValueAverage lower = average("000660", lowerTotal);
        DailyTradingValueAverage higher = average("005930", lowerTotal.add(BigInteger.ONE));

        assertThat(policy.rank(List.of(lower, higher))).containsExactly(higher, lower);
    }

    @Test
    void preservesOneWonDifferenceBetweenLargeTotals() {
        BigInteger lowerTotal = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(TRADING_DATES.size()));
        DailyTradingValueAverage lower = average("000660", lowerTotal);
        DailyTradingValueAverage higher = average("005930", lowerTotal.add(BigInteger.ONE));

        assertThat(policy.rank(List.of(lower, higher))).containsExactly(higher, lower);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 2",
            "0, 2, 1",
            "1, 0, 2",
            "1, 2, 0",
            "2, 0, 1",
            "2, 1, 0"
    })
    void breaksTiesBySymbolRegardlessOfInputOrder(int first, int second, int third) {
        DailyTradingValueAverage samsung = average("005930", 300L);
        DailyTradingValueAverage hynix = average("000660", 300L);
        DailyTradingValueAverage naver = average("035420", 300L);
        List<DailyTradingValueAverage> averages = List.of(samsung, hynix, naver);

        assertThat(policy.rank(List.of(
                averages.get(first), averages.get(second), averages.get(third)
        )))
                .containsExactly(hynix, samsung, naver);
    }

    @Test
    void retainsZeroTotalsAndDoesNotApplyMinimumOrCandidateLimit() {
        DailyTradingValueAverage zero = average("000660", 0L);
        DailyTradingValueAverage one = average("005930", 1L);
        DailyTradingValueAverage two = average("035420", 2L);

        assertThat(policy.rank(List.of(zero, one, two))).containsExactly(two, one, zero);
    }

    @Test
    void ranksAllZeroTotalsBySymbol() {
        DailyTradingValueAverage samsung = average("005930", 0L);
        DailyTradingValueAverage hynix = average("000660", 0L);

        assertThat(policy.rank(List.of(samsung, hynix))).containsExactly(hynix, samsung);
    }

    @Test
    void preservesSingleResult() {
        DailyTradingValueAverage average = average("005930", 300L);

        assertThat(policy.rank(List.of(average))).singleElement().isSameAs(average);
    }

    @Test
    void copiesInputWithoutChangingItsOrderAndReturnsImmutableResult() {
        DailyTradingValueAverage lower = average("000660", 100L);
        DailyTradingValueAverage higher = average("005930", 200L);
        List<DailyTradingValueAverage> input = new ArrayList<>(List.of(lower, higher));

        List<DailyTradingValueAverage> result = policy.rank(input);

        assertThat(input).containsExactly(lower, higher);
        input.clear();
        assertThat(result).containsExactly(higher, lower);
        assertThat(result.getFirst()).isSameAs(higher);
        assertThat(result.getLast()).isSameAs(lower);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsImmutableEmptyListForEmptyInput() {
        List<DailyTradingValueAverage> result = policy.rank(List.of());

        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add(average("005930", 300L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> policy.rank(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("averages must not be null.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsNullEntryWithoutDroppingIt(int nullIndex) {
        List<DailyTradingValueAverage> input = new ArrayList<>(List.of(
                average("000660", 100L), average("005930", 200L)
        ));
        input.set(nullIndex, null);

        assertThatThrownBy(() -> policy.rank(input))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("average must not be null.");
        assertThat(input).hasSize(2);
        assertThat(input.get(nullIndex)).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {100L, 200L})
    void rejectsDuplicateSymbolEvenWhenTotalsMatch(long duplicateTotal) {
        DailyTradingValueAverage original = average("005930", 100L);
        DailyTradingValueAverage duplicate = average("005930", duplicateTotal);

        assertThatThrownBy(() -> policy.rank(List.of(original, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate symbol in averages: 005930");
    }

    @Test
    void rejectsDifferentSelectionDates() {
        DailyTradingValueAverage reference = average("000660", 100L);
        DailyTradingValueAverage earlier = new DailyTradingValueAverage(
                "005930", SECOND_DATE, List.of(FIRST_DATE, SECOND_DATE), VENUE,
                BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> policy.rank(List.of(reference, earlier)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionAsOfDate must match across averages.");
    }

    @Test
    void rejectsDifferentTradingDayCounts() {
        DailyTradingValueAverage reference = average("000660", 100L);
        DailyTradingValueAverage shorter = new DailyTradingValueAverage(
                "005930", SELECTION_DATE, List.of(FIRST_DATE, SELECTION_DATE), VENUE,
                BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> policy.rank(List.of(reference, shorter)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must match across averages.");
    }

    @Test
    void rejectsDifferentDateListsEvenWhenDayCountsAndEndDatesMatch() {
        DailyTradingValueAverage reference = average("000660", 100L);
        DailyTradingValueAverage differentDates = new DailyTradingValueAverage(
                "005930", SELECTION_DATE,
                List.of(LocalDate.of(2026, 9, 18), SECOND_DATE, SELECTION_DATE), VENUE,
                BigInteger.valueOf(200L)
        );
        List<DailyTradingValueAverage> input = new ArrayList<>(List.of(reference, differentDates));

        assertThatThrownBy(() -> policy.rank(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must match across averages.");
        assertThat(input).containsExactly(reference, differentDates);
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void rejectsDifferentTradingVenueScopes(TradingVenueScope venue) {
        DailyTradingValueAverage reference = average("000660", 100L);
        DailyTradingValueAverage differentVenue = new DailyTradingValueAverage(
                "005930", SELECTION_DATE, TRADING_DATES, venue, BigInteger.valueOf(200L)
        );

        assertThatThrownBy(() -> policy.rank(List.of(reference, differentVenue)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingVenueScope must match across averages.");
    }

    private DailyTradingValueAverage average(String symbol, long totalTradingValueKrw) {
        return average(symbol, BigInteger.valueOf(totalTradingValueKrw));
    }

    private DailyTradingValueAverage average(String symbol, BigInteger totalTradingValueKrw) {
        return new DailyTradingValueAverage(
                symbol, SELECTION_DATE, TRADING_DATES, VENUE, totalTradingValueKrw
        );
    }
}
