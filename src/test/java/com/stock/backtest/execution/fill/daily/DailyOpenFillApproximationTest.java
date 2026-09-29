package com.stock.backtest.execution.fill.daily;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostCalculation;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyOpenFillApproximationTest {
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate FILL_DATE =
            LocalDate.of(2026, 9, 29);

    @Test
    void rejectsFillDateThatIsNotAfterDecisionDate() {
        TradeCostCalculation calculation = calculation();

        assertThatThrownBy(() -> new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                "005930",
                DECISION_DATE,
                DECISION_DATE,
                calculation
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fillDate must be after decisionDate.");
    }

    @Test
    void rejectsRequiredValuesThatCannotDescribeFill() {
        TradeCostCalculation calculation = calculation();

        assertThatThrownBy(() -> new DailyOpenFillApproximation(
                null,
                "005930",
                DECISION_DATE,
                FILL_DATE,
                calculation
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fillType must not be null.");
        assertThatThrownBy(() -> new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                " ",
                DECISION_DATE,
                FILL_DATE,
                calculation
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
        assertThatThrownBy(() -> new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                "005930",
                null,
                FILL_DATE,
                calculation
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("decisionDate must not be null.");
        assertThatThrownBy(() -> new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                "005930",
                DECISION_DATE,
                null,
                calculation
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fillDate must not be null.");
        assertThatThrownBy(() -> new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                "005930",
                DECISION_DATE,
                FILL_DATE,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tradeCostCalculation must not be null.");
    }

    private TradeCostCalculation calculation() {
        return new TradeCostCalculator().calculate(
                new TradeCostModel(
                        "BACKTEST_COST_V1",
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ),
                InvestmentAction.BUY,
                1L,
                70_000L
        );
    }
}
