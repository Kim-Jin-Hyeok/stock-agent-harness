package com.stock.strategy.universe.liquidity.evaluation.runner.config;

import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.SELECTION_DATE;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.TRADING_DATES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionEvaluationPropertiesTest {
    @Test
    void doesNotRequireEvaluationInputsWhenDisabled() {
        var properties = new DailyTradingValueSelectionEvaluationProperties(false, null, null, null, null, null, null);

        assertThat(properties.targetSymbols()).isEmpty();
        assertThat(properties.requiredTradingDates()).isEmpty();
        assertThat(properties.minimumAverageTradingValueKrw()).isNull();
        assertThat(properties.maxCandidateCount()).isNull();
        assertThatThrownBy(properties::toRequest).isInstanceOf(IllegalStateException.class)
                .hasMessage("Manual trading value selection evaluation must be enabled to create a request.");
    }

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void delegatesToExistingRequestAndPreservesExplicitCriteria(TradingVenueScope scope) {
        var properties = new DailyTradingValueSelectionEvaluationProperties(
                true, List.of("005930", "000660"), SELECTION_DATE, TRADING_DATES, scope, 123L, 7
        );

        var request = properties.toRequest();

        assertThat(request.targetSymbols()).containsExactly("000660", "005930");
        assertThat(request.selectionAsOfDate()).isEqualTo(SELECTION_DATE);
        assertThat(request.requiredTradingDates()).isEqualTo(TRADING_DATES);
        assertThat(request.expectedVenueScope()).isEqualTo(scope);
        assertThat(request.minimumAverageTradingValueKrw()).isEqualTo(123L);
        assertThat(request.maxCandidateCount()).isEqualTo(7);
    }

    @Test
    void defensivelyCopiesListsWithoutChangingCallerOrder() {
        var symbols = new ArrayList<>(List.of("005930", "000660"));
        var dates = new ArrayList<>(TRADING_DATES);
        var properties = new DailyTradingValueSelectionEvaluationProperties(
                true, symbols, SELECTION_DATE, dates, TradingVenueScope.INTEGRATED, 100L, 2
        );

        assertThat(symbols).containsExactly("005930", "000660");
        symbols.clear();
        dates.clear();

        assertThat(properties.toRequest().targetSymbols()).containsExactly("000660", "005930");
        assertThat(properties.toRequest().requiredTradingDates()).isEqualTo(TRADING_DATES);
        assertThatThrownBy(properties.targetSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(properties.requiredTradingDates()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"targets", "date", "dates", "scope", "minimum", "maximum"})
    void requiresEveryInputWhenEnabled(String missing) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationProperties(
                true, missing.equals("targets") ? null : List.of("005930"),
                missing.equals("date") ? null : SELECTION_DATE,
                missing.equals("dates") ? null : TRADING_DATES,
                missing.equals("scope") ? null : TradingVenueScope.INTEGRATED,
                missing.equals("minimum") ? null : 100L,
                missing.equals("maximum") ? null : 2
        )).isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsNonPositiveMinimumThroughExistingRequest(long minimum) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationProperties(
                true, List.of("005930"), SELECTION_DATE, TRADING_DATES, TradingVenueScope.INTEGRATED, minimum, 2
        )).isInstanceOf(IllegalArgumentException.class).hasMessage("minimumAverageTradingValueKrw must be positive.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveMaximumThroughExistingRequest(int maximum) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationProperties(
                true, List.of("005930"), SELECTION_DATE, TRADING_DATES, TradingVenueScope.INTEGRATED, 100L, maximum
        )).isInstanceOf(IllegalArgumentException.class).hasMessage("maxCandidateCount must be positive.");
    }

    @Test
    void rejectsDuplicateTargetsAndInvalidTradingWindow() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationProperties(
                true, List.of("005930", "005930"), SELECTION_DATE, TRADING_DATES,
                TradingVenueScope.INTEGRATED, 100L, 2
        )).isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate target symbol: 005930");
        List<LocalDate> invalidDates = List.of(SELECTION_DATE, SELECTION_DATE.plusDays(1));
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationProperties(
                true, List.of("005930"), SELECTION_DATE, invalidDates, TradingVenueScope.INTEGRATED, 100L, 2
        )).isInstanceOf(IllegalArgumentException.class).hasMessage("tradingDates must not be after selectionAsOfDate.");
    }
}
