package com.stock.backtest.context.portfolio;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.portfolio.PortfolioPosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestPortfolioEvaluationContextFactoryTest {
    private static final LocalDate EVALUATION_DATE =
            LocalDate.of(2026, 9, 29);
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:30:00Z");

    private final BacktestPortfolioEvaluationContextFactory factory =
            new BacktestPortfolioEvaluationContextFactory();

    @Test
    void createsCashOnlyEvaluationContext() {
        BacktestPortfolioState portfolioState =
                BacktestPortfolioState.withCash(1_000_000L);

        BacktestPortfolioEvaluationContext context = factory.create(
                portfolioState,
                EVALUATION_DATE,
                EVALUATED_AT,
                Map.of()
        );

        assertThat(context.evaluationDate()).isEqualTo(EVALUATION_DATE);
        assertThat(context.evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(context.portfolioSnapshot().cashAmountKrw())
                .isEqualTo(1_000_000L);
        assertThat(context.portfolioSnapshot().totalAssetAmountKrw())
                .isEqualTo(1_000_000L);
        assertThat(context.portfolioSnapshot().positions()).isEmpty();
        assertThat(context.currentPrices()).isEmpty();
    }

    @Test
    void evaluatesPositionsAndCreatesCandidateCurrentPrices() {
        BacktestPosition samsung = new BacktestPosition(
                "005930",
                10L,
                70_000L
        );
        BacktestPosition hynix = new BacktestPosition(
                "000660",
                2L,
                180_000L
        );
        BacktestPortfolioState portfolioState = new BacktestPortfolioState(
                300_000L,
                List.of(samsung, hynix)
        );

        BacktestPortfolioEvaluationContext context = factory.create(
                portfolioState,
                EVALUATION_DATE,
                EVALUATED_AT,
                Map.of(
                        "005930",
                        bar(75_000L),
                        "000660",
                        bar(190_000L),
                        "035420",
                        bar(250_000L)
                )
        );

        assertThat(context.portfolioSnapshot().cashAmountKrw())
                .isEqualTo(300_000L);
        assertThat(context.portfolioSnapshot().totalAssetAmountKrw())
                .isEqualTo(1_430_000L);
        assertThat(context.portfolioSnapshot().positions())
                .containsExactly(
                        new PortfolioPosition(
                                "005930",
                                10L,
                                70_000L,
                                700_000L
                        ),
                        new PortfolioPosition(
                                "000660",
                                2L,
                                180_000L,
                                360_000L
                        )
                );
        assertThat(context.currentPrices())
                .extracting(CurrentPriceSnapshot::symbol)
                .containsExactly("000660", "005930", "035420");
        assertThat(context.currentPrices())
                .allMatch(price -> price.observedAt().equals(EVALUATED_AT));
        assertThat(context.findCurrentPrice("035420"))
                .contains(new CurrentPriceSnapshot(
                        "035420",
                        250_000L,
                        EVALUATED_AT
                ));
        assertThat(portfolioState.cashAmountKrw()).isEqualTo(300_000L);
        assertThat(portfolioState.positions())
                .containsExactly(samsung, hynix);
    }

    @Test
    void rejectsMissingDailyPriceBarForPosition() {
        BacktestPortfolioState portfolioState = new BacktestPortfolioState(
                300_000L,
                List.of(new BacktestPosition(
                        "005930",
                        10L,
                        70_000L
                ))
        );

        assertThatThrownBy(() -> factory.create(
                portfolioState,
                EVALUATION_DATE,
                EVALUATED_AT,
                Map.of("000660", bar(190_000L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Daily price bar is required for backtest position. "
                                + "symbol=005930"
                );
    }

    @Test
    void rejectsDailyPriceBarOutsideEvaluationDate() {
        BacktestPortfolioState portfolioState =
                BacktestPortfolioState.withCash(1_000_000L);
        DailyPriceBar futureBar = new DailyPriceBar(
                EVALUATION_DATE.plusDays(1),
                70_000L,
                72_000L,
                69_000L,
                71_000L,
                1_000_000L
        );

        assertThatThrownBy(() -> factory.create(
                portfolioState,
                EVALUATION_DATE,
                EVALUATED_AT,
                Map.of("005930", futureBar)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "dailyPriceBar.tradingDate must match evaluationDate. "
                                + "symbol=005930"
                );
    }

    @Test
    void rejectsEvaluationInstantOutsideKoreanEvaluationDate() {
        BacktestPortfolioState portfolioState =
                BacktestPortfolioState.withCash(1_000_000L);

        assertThatThrownBy(() -> factory.create(
                portfolioState,
                EVALUATION_DATE,
                Instant.parse("2026-09-28T06:30:00Z"),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "evaluatedAt must belong to evaluationDate "
                                + "in Asia/Seoul."
                );
    }

    private DailyPriceBar bar(long closePriceKrw) {
        return new DailyPriceBar(
                EVALUATION_DATE,
                closePriceKrw - 1_000L,
                closePriceKrw + 1_000L,
                closePriceKrw - 2_000L,
                closePriceKrw,
                1_000_000L
        );
    }
}
