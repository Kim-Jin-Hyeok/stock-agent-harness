package com.stock.strategy.analysis.volatility.atr;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.indicator.volatility.atr.WilderAverageTrueRangeCalculator;
import com.stock.strategy.indicator.volatility.atr.config.ConfiguredStrategyAverageTrueRangePeriod;
import com.stock.strategy.indicator.volatility.atr.config.StrategyAverageTrueRangeProperties;
import com.stock.strategy.indicator.volatility.atr.policy.StrategyAverageTrueRangePeriodPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AverageTrueRangeAnalysisServiceTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    private final AverageTrueRangeAnalysisService service = service();

    @Test
    void analyzesHistoryUsingStrategyPeriod() {
        AverageTrueRangeAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                history(
                        bar(1, 100L, 100L, 100L, 100L),
                        bar(2, 100L, 110L, 95L, 105L),
                        bar(3, 105L, 112L, 100L, 110L),
                        bar(4, 110L, 115L, 104L, 108L),
                        bar(5, 108L, 120L, 108L, 118L)
                )
        );

        assertThat(result.status())
                .isEqualTo(AverageTrueRangeAnalysisStatus.ANALYZED);
        assertThat(result.strategyIdentity()).isEqualTo(STRATEGY_IDENTITY);
        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.requiredBarCount()).isEqualTo(4);
        assertThat(result.availableBarCount()).isEqualTo(5);
        assertThat(result.averageTrueRange().period()).isEqualTo(3);
        assertThat(result.averageTrueRange().averageTrueRangeKrw())
                .isEqualByComparingTo("12.44");
    }

    @Test
    void returnsInsufficientDataWithBarCounts() {
        AverageTrueRangeAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                history(
                        bar(1, 100L, 100L, 100L, 100L),
                        bar(2, 100L, 110L, 95L, 105L),
                        bar(3, 105L, 112L, 100L, 110L)
                )
        );

        assertThat(result.status())
                .isEqualTo(AverageTrueRangeAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(result.requiredBarCount()).isEqualTo(4);
        assertThat(result.availableBarCount()).isEqualTo(3);
        assertThat(result.averageTrueRange()).isNull();
    }

    @Test
    void rejectsUnregisteredStrategyIdentity() {
        InvestmentStrategyIdentity unknown = new InvestmentStrategyIdentity(
                "SWING_V2",
                2,
                InvestmentHorizon.SWING
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.analyze(unknown, history(
                        bar(1, 100L, 100L, 100L, 100L)
                )))
                .withMessage(
                        "Strategy average true range period not found: "
                                + unknown
                );
    }

    @Test
    void rejectsNullInputs() {
        DailyPriceHistory history = history(
                bar(1, 100L, 100L, 100L, 100L)
        );

        assertThatNullPointerException()
                .isThrownBy(() -> service.analyze(null, history))
                .withMessage("strategyIdentity must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> service.analyze(STRATEGY_IDENTITY, null))
                .withMessage("history must not be null.");
    }

    private AverageTrueRangeAnalysisService service() {
        StrategyDailyPriceHistoryPolicy historyPolicy =
                new StrategyDailyPriceHistoryPolicy(
                        new StrategyDailyPriceHistoryProperties(List.of(
                                new ConfiguredStrategyDailyPriceHistoryLimit(
                                        STRATEGY_IDENTITY.strategyId(),
                                        STRATEGY_IDENTITY.strategyVersion(),
                                        STRATEGY_IDENTITY.horizon(),
                                        120
                                )
                        ))
                );
        StrategyAverageTrueRangePeriodPolicy periodPolicy =
                new StrategyAverageTrueRangePeriodPolicy(
                        new StrategyAverageTrueRangeProperties(List.of(
                                new ConfiguredStrategyAverageTrueRangePeriod(
                                        STRATEGY_IDENTITY.strategyId(),
                                        STRATEGY_IDENTITY.strategyVersion(),
                                        STRATEGY_IDENTITY.horizon(),
                                        3
                                )
                        )),
                        historyPolicy
                );
        return new AverageTrueRangeAnalysisService(
                periodPolicy,
                new WilderAverageTrueRangeCalculator()
        );
    }

    private DailyPriceHistory history(DailyPriceBar... bars) {
        return new DailyPriceHistory("005930", List.of(bars));
    }

    private DailyPriceBar bar(
            int dayOfMonth,
            long openPriceKrw,
            long highPriceKrw,
            long lowPriceKrw,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                LocalDate.of(2026, 9, dayOfMonth),
                openPriceKrw,
                highPriceKrw,
                lowPriceKrw,
                closePriceKrw,
                1_000L
        );
    }
}
