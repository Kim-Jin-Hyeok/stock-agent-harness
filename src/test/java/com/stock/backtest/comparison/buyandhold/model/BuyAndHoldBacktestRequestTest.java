package com.stock.backtest.comparison.buyandhold.model;

import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyAndHoldBacktestRequestTest {
    private static final Instant FIRST = Instant.parse("2026-08-31T15:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-09-01T15:00:00Z");
    private static final TradeCostModel COST_MODEL = new TradeCostModel(
            "ZERO", 1, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
    );

    @Test
    void copiesValuationInstantsAndUsesKoreanDates() {
        List<Instant> instants = new ArrayList<>(List.of(FIRST, SECOND));
        BuyAndHoldBacktestRequest request = request(999L, "0.1", instants);
        instants.clear();

        assertThat(request.valuationInstants()).containsExactly(FIRST, SECOND);
        assertThat(request.valuationDates()).containsExactly(
                java.time.LocalDate.of(2026, 9, 1),
                java.time.LocalDate.of(2026, 9, 2)
        );
        assertThat(request.buyBudgetAmountKrw()).isEqualTo(99L);
        assertThatThrownBy(() -> request.valuationInstants().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.1", "1.01"})
    void rejectsInvalidAllocation(String ratio) {
        assertThatThrownBy(() -> request(1_000L, ratio, List.of(FIRST)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsNonPositiveCash(long cash) {
        assertThatThrownBy(() -> request(cash, "1", List.of(FIRST)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyDuplicateOrUnorderedValuationDates() {
        for (List<Instant> instants : List.of(
                List.<Instant>of(),
                List.of(FIRST, FIRST.plusSeconds(60)),
                List.of(SECOND, FIRST)
        )) {
            assertThatThrownBy(() -> request(1_000L, "1", instants))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsBlankSymbolAndNullComponents() {
        assertThatThrownBy(() -> new BuyAndHoldBacktestRequest(
                " ", 1_000L, BigDecimal.ONE, List.of(FIRST), COST_MODEL
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuyAndHoldBacktestRequest(
                "005930", 1_000L, null, List.of(FIRST), COST_MODEL
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> request(1_000L, "1", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuyAndHoldBacktestRequest(
                "005930", 1_000L, BigDecimal.ONE, List.of(FIRST), null
        )).isInstanceOf(NullPointerException.class);
    }

    private BuyAndHoldBacktestRequest request(
            long cash, String ratio, List<Instant> instants
    ) {
        return new BuyAndHoldBacktestRequest(
                "005930", cash, new BigDecimal(ratio), instants, COST_MODEL
        );
    }
}
