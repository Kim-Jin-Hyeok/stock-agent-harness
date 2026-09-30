package com.stock.backtest.performance.equity;

import com.stock.backtest.context.portfolio.BacktestPortfolioEvaluationContext;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestEquitySnapshotTest {
    private static final LocalDate VALUATION_DATE =
            LocalDate.of(2026, 9, 29);
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T00:10:00Z");

    @Test
    void createsSnapshotFromCurrentPortfolioEvaluation() {
        BacktestPortfolioEvaluationContext context =
                new BacktestPortfolioEvaluationContext(
                        VALUATION_DATE,
                        EVALUATED_AT,
                        new PortfolioSnapshot(
                                300_000L,
                                1_100_000L,
                                List.of(new PortfolioPosition(
                                        "005930",
                                        10L,
                                        70_000L,
                                        700_000L
                                ))
                        ),
                        List.of(new CurrentPriceSnapshot(
                                "005930",
                                80_000L,
                                EVALUATED_AT
                        ))
                );

        BacktestEquitySnapshot snapshot =
                BacktestEquitySnapshot.from(context);

        assertThat(snapshot.cashAmountKrw()).isEqualTo(300_000L);
        assertThat(snapshot.positionEvaluationAmountKrw())
                .isEqualTo(800_000L);
        assertThat(snapshot.totalAssetAmountKrw())
                .isEqualTo(1_100_000L);
    }

    @Test
    void rejectsTotalAssetThatDoesNotMatchComponents() {
        assertThatThrownBy(() -> new BacktestEquitySnapshot(
                VALUATION_DATE,
                EVALUATED_AT,
                300_000L,
                800_000L,
                1_000_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "totalAssetAmountKrw must equal cash and position "
                                + "evaluation amount."
                );
    }

    @Test
    void rejectsEvaluationInstantOutsideValuationDate() {
        assertThatThrownBy(() -> new BacktestEquitySnapshot(
                VALUATION_DATE,
                Instant.parse("2026-09-28T00:10:00Z"),
                1_000_000L,
                0L,
                1_000_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "evaluatedAt must belong to valuationDate "
                                + "in Asia/Seoul."
                );
    }
}
