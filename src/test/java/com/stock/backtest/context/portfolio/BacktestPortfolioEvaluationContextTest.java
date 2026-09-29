package com.stock.backtest.context.portfolio;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestPortfolioEvaluationContextTest {
    private static final LocalDate EVALUATION_DATE =
            LocalDate.of(2026, 9, 29);
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:30:00Z");

    @Test
    void copiesCurrentPricesAndFindsPriceBySymbol() {
        CurrentPriceSnapshot currentPrice = new CurrentPriceSnapshot(
                "005930",
                75_000L,
                EVALUATED_AT
        );
        List<CurrentPriceSnapshot> currentPrices = new ArrayList<>();
        currentPrices.add(currentPrice);

        BacktestPortfolioEvaluationContext context =
                new BacktestPortfolioEvaluationContext(
                        EVALUATION_DATE,
                        EVALUATED_AT,
                        portfolioSnapshot(),
                        currentPrices
                );
        currentPrices.clear();

        assertThat(context.currentPrices()).containsExactly(currentPrice);
        assertThat(context.findCurrentPrice("005930"))
                .contains(currentPrice);
        assertThat(context.findCurrentPrice("000660")).isEmpty();
        assertThatThrownBy(() -> context.currentPrices().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsCurrentPriceObservedAtDifferentTime() {
        CurrentPriceSnapshot currentPrice = new CurrentPriceSnapshot(
                "005930",
                75_000L,
                EVALUATED_AT.minusSeconds(1)
        );

        assertThatThrownBy(() -> new BacktestPortfolioEvaluationContext(
                EVALUATION_DATE,
                EVALUATED_AT,
                portfolioSnapshot(),
                List.of(currentPrice)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "currentPrice.observedAt must match evaluatedAt. "
                                + "symbol=005930"
                );
    }

    @Test
    void rejectsTotalAssetThatDoesNotMatchEvaluatedPositions() {
        PortfolioSnapshot invalidPortfolioSnapshot = new PortfolioSnapshot(
                300_000L,
                1_000_000L,
                List.of(new PortfolioPosition(
                        "005930",
                        10L,
                        70_000L,
                        700_000L
                ))
        );
        CurrentPriceSnapshot currentPrice = new CurrentPriceSnapshot(
                "005930",
                75_000L,
                EVALUATED_AT
        );

        assertThatThrownBy(() -> new BacktestPortfolioEvaluationContext(
                EVALUATION_DATE,
                EVALUATED_AT,
                invalidPortfolioSnapshot,
                List.of(currentPrice)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "portfolioSnapshot.totalAssetAmountKrw must match "
                                + "cash and evaluated positions."
                );
    }

    @Test
    void rejectsDuplicateCurrentPriceSymbol() {
        CurrentPriceSnapshot currentPrice = new CurrentPriceSnapshot(
                "005930",
                75_000L,
                EVALUATED_AT
        );

        assertThatThrownBy(() -> new BacktestPortfolioEvaluationContext(
                EVALUATION_DATE,
                EVALUATED_AT,
                portfolioSnapshot(),
                List.of(currentPrice, currentPrice)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "currentPrices must not contain duplicate symbol. "
                                + "symbol=005930"
                );
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                300_000L,
                1_050_000L,
                List.of(new PortfolioPosition(
                        "005930",
                        10L,
                        70_000L,
                        700_000L
                ))
        );
    }
}
