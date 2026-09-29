package com.stock.backtest.portfolio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestPortfolioStateTest {

    @Test
    void copiesPositionsAndFindsPositionBySymbol() {
        List<BacktestPosition> positions = new ArrayList<>();
        BacktestPosition position = new BacktestPosition(
                "005930",
                10L,
                70_000L
        );
        positions.add(position);

        BacktestPortfolioState state = new BacktestPortfolioState(
                1_000_000L,
                positions
        );
        positions.clear();

        assertThat(state.positions()).containsExactly(position);
        assertThat(state.findPosition("005930")).contains(position);
        assertThat(state.findPosition("000660")).isEmpty();
        assertThatThrownBy(() -> state.positions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createsInitialStateWithCashOnly() {
        BacktestPortfolioState state =
                BacktestPortfolioState.withCash(1_000_000L);

        assertThat(state.cashAmountKrw()).isEqualTo(1_000_000L);
        assertThat(state.positions()).isEmpty();
    }

    @Test
    void rejectsInvalidPortfolioState() {
        BacktestPosition position = new BacktestPosition(
                "005930",
                10L,
                70_000L
        );

        assertThatThrownBy(() -> new BacktestPortfolioState(
                -1L,
                List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cashAmountKrw must not be negative.");
        assertThatThrownBy(() -> new BacktestPortfolioState(0L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("positions must not be null.");
        assertThatThrownBy(() -> new BacktestPortfolioState(
                0L,
                List.of(position, position)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "positions must not contain duplicate symbols."
                );
        assertThatThrownBy(() -> new BacktestPosition(" ", 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
        assertThatThrownBy(() -> new BacktestPosition("005930", 0L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive.");
        assertThatThrownBy(() -> new BacktestPosition("005930", 1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "averageExecutionPriceKrw must be positive."
                );
    }
}
