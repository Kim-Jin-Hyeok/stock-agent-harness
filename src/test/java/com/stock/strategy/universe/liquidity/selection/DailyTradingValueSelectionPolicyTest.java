package com.stock.strategy.universe.liquidity.selection;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionPolicyTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES =
            List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;

    private final DailyTradingValueSelectionPolicy policy = new DailyTradingValueSelectionPolicy(
            new DailyTradingValueRankingPolicy()
    );

    @Test
    void preservesEveryAverageWithRankAndSelectionReason() {
        DailyTradingValueAverage first = average("005930", 900L);
        DailyTradingValueAverage second = average("000660", 600L);
        DailyTradingValueAverage limited = average("035420", 450L);
        DailyTradingValueAverage below = average("005380", 30L);

        List<DailyTradingValueSelectionResult> results = policy.select(
                List.of(below, limited, second, first), 100L, 2
        );

        assertThat(results).containsExactly(
                result(first, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(second, 2, DailyTradingValueSelectionStatus.SELECTED),
                result(limited, 3, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT),
                result(below, 4, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
        assertThat(results.getFirst().average()).isSameAs(first);
        assertThat(results.getLast().average()).isSameAs(below);
    }

    @Test
    void selectsAverageEqualToMinimumAndDistinguishesOneWonBoundary() {
        DailyTradingValueAverage below = average("000660", 299L);
        DailyTradingValueAverage equal = average("005930", 300L);
        DailyTradingValueAverage above = average("035420", 301L);

        assertThat(policy.select(List.of(below, equal, above), 100L, 3)).containsExactly(
                result(above, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(equal, 2, DailyTradingValueSelectionStatus.SELECTED),
                result(below, 3, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
    }

    @Test
    void belowMinimumResultsDoNotConsumeSlotsOrRequireFillingTheLimit() {
        DailyTradingValueAverage below = average("000660", 299L);
        DailyTradingValueAverage selected = average("005930", 300L);
        DailyTradingValueAverage zero = average("035420", 0L);

        assertThat(policy.select(List.of(below, selected, zero), 100L, 2)).containsExactly(
                result(selected, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(below, 2, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM),
                result(zero, 3, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
    }

    @Test
    void keepsBelowMinimumReasonAfterCandidateLimitIsReached() {
        DailyTradingValueAverage selected = average("005930", 600L);
        DailyTradingValueAverage limited = average("000660", 300L);
        DailyTradingValueAverage below = average("035420", 299L);

        assertThat(policy.select(List.of(below, limited, selected), 100L, 1)).containsExactly(
                result(selected, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(limited, 2, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT),
                result(below, 3, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
    }

    @Test
    void retainsAllZeroResultsAsBelowMinimum() {
        DailyTradingValueAverage samsung = average("005930", 0L);
        DailyTradingValueAverage hynix = average("000660", 0L);

        assertThat(policy.select(List.of(samsung, hynix), 1L, 1)).containsExactly(
                result(hynix, 1, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM),
                result(samsung, 2, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
    }

    @Test
    void selectsAllEligibleResultsWhenLimitExceedsInputSize() {
        DailyTradingValueAverage first = average("005930", 600L);
        DailyTradingValueAverage second = average("000660", 300L);

        assertThat(policy.select(List.of(second, first), 100L, Integer.MAX_VALUE))
                .containsExactly(
                        result(first, 1, DailyTradingValueSelectionStatus.SELECTED),
                        result(second, 2, DailyTradingValueSelectionStatus.SELECTED)
                );
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
    void tieAtCandidateLimitIsResolvedBySymbolRegardlessOfInputOrder(
            int first, int second, int third
    ) {
        DailyTradingValueAverage samsung = average("005930", 300L);
        DailyTradingValueAverage hynix = average("000660", 300L);
        DailyTradingValueAverage naver = average("035420", 300L);
        List<DailyTradingValueAverage> averages = List.of(samsung, hynix, naver);

        assertThat(policy.select(List.of(
                averages.get(first), averages.get(second), averages.get(third)
        ), 100L, 1)).containsExactly(
                result(hynix, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(samsung, 2, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT),
                result(naver, 3, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT)
        );
    }

    @Test
    void comparesLargeTotalsAndMinimumExactlyWithoutOverflowOrRounding() {
        BigInteger minimumTotal = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(TRADING_DATES.size()));
        DailyTradingValueAverage below = average("035420", minimumTotal.subtract(BigInteger.ONE));
        DailyTradingValueAverage equal = average("000660", minimumTotal);
        DailyTradingValueAverage above = average("005930", minimumTotal.add(BigInteger.ONE));

        assertThat(policy.select(List.of(below, equal, above), Long.MAX_VALUE, 1))
                .containsExactly(
                        result(above, 1, DailyTradingValueSelectionStatus.SELECTED),
                        result(equal, 2, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT),
                        result(below, 3, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
                );
    }

    @Test
    void reusesPolicyWithoutRetainingPreviousSelectionCountOrCriteria() {
        DailyTradingValueAverage first = average("005930", 600L);
        DailyTradingValueAverage second = average("000660", 300L);
        List<DailyTradingValueAverage> averages = List.of(second, first);

        assertThat(policy.select(averages, 100L, 1)).containsExactly(
                result(first, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(second, 2, DailyTradingValueSelectionStatus.CANDIDATE_LIMIT)
        );
        assertThat(policy.select(averages, 201L, 2)).containsExactly(
                result(first, 1, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM),
                result(second, 2, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
        assertThat(policy.select(averages, 100L, 2)).containsExactly(
                result(first, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(second, 2, DailyTradingValueSelectionStatus.SELECTED)
        );
    }

    @Test
    void leavesCallerOrderUnchangedAndReturnsImmutableIndependentList() {
        DailyTradingValueAverage below = average("000660", 299L);
        DailyTradingValueAverage selected = average("005930", 300L);
        List<DailyTradingValueAverage> input = new ArrayList<>(List.of(below, selected));

        List<DailyTradingValueSelectionResult> results = policy.select(input, 100L, 1);

        assertThat(input).containsExactly(below, selected);
        input.clear();
        assertThat(results).containsExactly(
                result(selected, 1, DailyTradingValueSelectionStatus.SELECTED),
                result(below, 2, DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM)
        );
        assertThatThrownBy(results::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsImmutableEmptyListForEmptyInputWithValidCriteria() {
        List<DailyTradingValueSelectionResult> results = policy.select(List.of(), 100L, 1);

        assertThat(results).isEmpty();
        assertThatThrownBy(() -> results.add(result(
                average("005930", 300L), 1, DailyTradingValueSelectionStatus.SELECTED
        )))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectsInvalidMinimumEvenForEmptyInput(long minimumAverageTradingValueKrw) {
        assertThatThrownBy(() -> policy.select(List.of(), minimumAverageTradingValueKrw, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minimumAverageTradingValueKrw must be positive.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsInvalidLimitEvenForEmptyInput(int maxCandidateCount) {
        assertThatThrownBy(() -> policy.select(List.of(), 100L, maxCandidateCount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCandidateCount must be positive.");
    }

    @Test
    void rejectsNullRankingPolicy() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rankingPolicy must not be null.");
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> policy.select(null, 100L, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("averages must not be null.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsNullEntryRatherThanTreatingItAsBelowMinimum(int nullIndex) {
        List<DailyTradingValueAverage> input = new ArrayList<>(List.of(
                average("000660", 600L), average("005930", 0L)
        ));
        input.set(nullIndex, null);

        assertThatThrownBy(() -> policy.select(input, 100L, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("average must not be null.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 300L})
    void rejectsDuplicateEvenIfOneResultWouldBeBelowMinimum(long duplicateTotal) {
        assertThatThrownBy(() -> policy.select(List.of(
                average("005930", 600L), average("005930", duplicateTotal)
        ), 100L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate symbol in averages: 005930");
    }

    @Test
    void validatesDifferentSelectionDatesBeforeFiltering() {
        DailyTradingValueAverage selected = average("000660", 600L);
        DailyTradingValueAverage earlierZero = new DailyTradingValueAverage(
                "005930", SECOND_DATE, List.of(FIRST_DATE, SECOND_DATE), VENUE, BigInteger.ZERO
        );

        assertThatThrownBy(() -> policy.select(List.of(selected, earlierZero), 100L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionAsOfDate must match across averages.");
    }

    @Test
    void validatesDifferentTradingDayCountsBeforeFiltering() {
        DailyTradingValueAverage selected = average("000660", 600L);
        DailyTradingValueAverage shorterZero = new DailyTradingValueAverage(
                "005930", SELECTION_DATE, List.of(FIRST_DATE, SELECTION_DATE), VENUE,
                BigInteger.ZERO
        );

        assertThatThrownBy(() -> policy.select(List.of(selected, shorterZero), 100L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must match across averages.");
    }

    @Test
    void validatesExactDateListsBeforeApplyingCandidateLimit() {
        DailyTradingValueAverage selected = average("000660", 900L);
        DailyTradingValueAverage differentDates = new DailyTradingValueAverage(
                "005930", SELECTION_DATE,
                List.of(LocalDate.of(2026, 9, 18), SECOND_DATE, SELECTION_DATE), VENUE,
                BigInteger.valueOf(600L)
        );
        List<DailyTradingValueAverage> input = new ArrayList<>(List.of(selected, differentDates));

        assertThatThrownBy(() -> policy.select(input, 100L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tradingDates must match across averages.");
        assertThat(input).containsExactly(selected, differentDates);
    }

    @ParameterizedTest
    @EnumSource(value = TradingVenueScope.class, names = {"KRX", "NXT"})
    void validatesVenueConflictEvenIfResultWouldBeBelowMinimum(TradingVenueScope venue) {
        DailyTradingValueAverage selected = average("000660", 600L);
        DailyTradingValueAverage differentVenue = new DailyTradingValueAverage(
                "005930", SELECTION_DATE, TRADING_DATES, venue, BigInteger.ZERO
        );

        assertThatThrownBy(() -> policy.select(List.of(selected, differentVenue), 100L, 1))
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

    private DailyTradingValueSelectionResult result(
            DailyTradingValueAverage average,
            int rank,
            DailyTradingValueSelectionStatus status
    ) {
        return new DailyTradingValueSelectionResult(average, rank, status);
    }
}
