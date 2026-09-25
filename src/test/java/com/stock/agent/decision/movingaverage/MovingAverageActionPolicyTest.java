package com.stock.agent.decision.movingaverage;

import com.stock.agent.InvestmentAction;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageActionPolicyTest {
    private static final String SYMBOL = "005930";

    private final MovingAverageActionPolicy policy =
            new MovingAverageActionPolicy();

    @Test
    void returnsBuyForGoldenCrossWithoutPosition() {
        InvestmentAction action = policy.decide(
                MovingAverageCrossoverSignal.GOLDEN_CROSS,
                SYMBOL,
                portfolioWithoutPosition()
        );

        assertThat(action).isEqualTo(InvestmentAction.BUY);
    }

    @Test
    void returnsHoldForGoldenCrossWithPosition() {
        InvestmentAction action = policy.decide(
                MovingAverageCrossoverSignal.GOLDEN_CROSS,
                SYMBOL,
                portfolioWithPosition()
        );

        assertThat(action).isEqualTo(InvestmentAction.HOLD);
    }

    @Test
    void returnsSellForDeadCrossWithPosition() {
        InvestmentAction action = policy.decide(
                MovingAverageCrossoverSignal.DEAD_CROSS,
                SYMBOL,
                portfolioWithPosition()
        );

        assertThat(action).isEqualTo(InvestmentAction.SELL);
    }

    @Test
    void returnsHoldForDeadCrossWithoutPosition() {
        InvestmentAction action = policy.decide(
                MovingAverageCrossoverSignal.DEAD_CROSS,
                SYMBOL,
                portfolioWithoutPosition()
        );

        assertThat(action).isEqualTo(InvestmentAction.HOLD);
    }

    @Test
    void returnsHoldWhenCrossoverSignalIsNone() {
        InvestmentAction action = policy.decide(
                MovingAverageCrossoverSignal.NONE,
                SYMBOL,
                portfolioWithPosition()
        );

        assertThat(action).isEqualTo(InvestmentAction.HOLD);
    }

    @Test
    void rejectsNullCrossoverSignal() {
        assertThatThrownBy(() -> policy.decide(
                null,
                SYMBOL,
                portfolioWithoutPosition()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("crossoverSignal must not be null.");
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> policy.decide(
                MovingAverageCrossoverSignal.NONE,
                " ",
                portfolioWithoutPosition()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }

    @Test
    void rejectsNullPortfolioSnapshot() {
        assertThatThrownBy(() -> policy.decide(
                MovingAverageCrossoverSignal.NONE,
                SYMBOL,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("portfolioSnapshot must not be null.");
    }

    private PortfolioSnapshot portfolioWithoutPosition() {
        return new PortfolioSnapshot(
                1_000_000L,
                1_000_000L,
                List.of()
        );
    }

    private PortfolioSnapshot portfolioWithPosition() {
        return new PortfolioSnapshot(
                300_000L,
                1_000_000L,
                List.of(new PortfolioPosition(
                        SYMBOL,
                        10L,
                        70_000L,
                        700_000L
                ))
        );
    }
}
