package com.stock.portfolio;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.stock.portfolio.support.PortfolioSnapshotStoreFixture.create;

class PortfolioServiceTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private static final InvestmentStrategyIdentity OTHER_STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING);
    private static final InvestmentStrategyIdentity OTHER_VERSION_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 2, InvestmentHorizon.DAY_TRADING);
    private final PortfolioSnapshotStore store = create();
    private final PortfolioService portfolioService = new PortfolioService(store);

    @Test
    void applyBuyAddsNewPosition() {
        PortfolioSnapshot result = portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                100_000L
        );

        assertThat(result.cashAmountKrw()).isEqualTo(9_000_000L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(result.positions()).hasSize(1);

        PortfolioPosition position = result.positions().getFirst();

        assertThat(position.symbol()).isEqualTo("TEST");
        assertThat(position.quantity()).isEqualTo(10L);
        assertThat(position.averagePriceKrw()).isEqualTo(100_000L);
        assertThat(position.marketValueKrw()).isEqualTo(1_000_000L);
    }

    @Test
    void applyBuyMergesExistingPosition() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                100_000L
        );

        PortfolioSnapshot result = portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                5L,
                120_000L
        );

        assertThat(result.cashAmountKrw()).isEqualTo(8_400_000L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(result.positions()).hasSize(1);

        PortfolioPosition position = result.positions().getFirst();

        assertThat(position.symbol()).isEqualTo("TEST");
        assertThat(position.quantity()).isEqualTo(15L);
        assertThat(position.averagePriceKrw()).isEqualTo(106_666L);
        assertThat(position.marketValueKrw()).isEqualTo(1_600_000L);
    }

    @Test
    void applyBuyFillPreservesExactExecutionAmount() {
        PortfolioSnapshot result = portfolioService.applyBuyFill(
                STRATEGY_IDENTITY,
                "TEST",
                2L,
                139_001L
        );

        assertThat(result.cashAmountKrw()).isEqualTo(9_860_999L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(10_000_000L);

        PortfolioPosition position = result.positions().getFirst();
        assertThat(position.quantity()).isEqualTo(2L);
        assertThat(position.averagePriceKrw()).isEqualTo(69_500L);
        assertThat(position.marketValueKrw()).isEqualTo(139_001L);
    }

    @Test
    void applyBuyFillMergesUsingExactExecutionAmounts() {
        portfolioService.applyBuyFill(
                STRATEGY_IDENTITY,
                "TEST",
                2L,
                139_001L
        );

        PortfolioSnapshot result = portfolioService.applyBuyFill(
                STRATEGY_IDENTITY,
                "TEST",
                3L,
                209_999L
        );

        assertThat(result.cashAmountKrw()).isEqualTo(9_651_000L);

        PortfolioPosition position = result.positions().getFirst();
        assertThat(position.quantity()).isEqualTo(5L);
        assertThat(position.averagePriceKrw()).isEqualTo(69_800L);
        assertThat(position.marketValueKrw()).isEqualTo(349_000L);
    }

    @Test
    void applySellExistingPosition() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                100_000L
        );

        PortfolioSnapshot result = portfolioService.applySell(
                STRATEGY_IDENTITY,
                "TEST",
                5L,
                120_000L
        );

        assertThat(result.cashAmountKrw()).isEqualTo(9_600_000L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(10_100_000L);
        assertThat(result.positions()).hasSize(1);

        PortfolioPosition position = result.positions().getFirst();

        assertThat(position.symbol()).isEqualTo("TEST");
        assertThat(position.quantity()).isEqualTo(5L);
        assertThat(position.averagePriceKrw()).isEqualTo(100_000L);
        assertThat(position.marketValueKrw()).isEqualTo(500_000L);
    }

    @Test
    void applySellRemovesPositionWhenQuantityBecomesZero() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                100_000L
        );

        PortfolioSnapshot result = portfolioService.applySell(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                120_000L
        );

        assertThat(result.cashAmountKrw()).isEqualTo(10_200_000L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(10_200_000L);
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void applySellFillPreservesExactExecutionAmount() {
        portfolioService.applyBuyFill(
                STRATEGY_IDENTITY,
                "TEST",
                3L,
                209_701L
        );

        PortfolioSnapshot result = portfolioService.applySellFill(
                STRATEGY_IDENTITY,
                "TEST",
                1L,
                71_001L
        );

        assertThat(result.cashAmountKrw()).isEqualTo(9_861_300L);
        assertThat(result.totalAssetAmountKrw()).isEqualTo(10_001_100L);

        PortfolioPosition position = result.positions().getFirst();
        assertThat(position.quantity()).isEqualTo(2L);
        assertThat(position.averagePriceKrw()).isEqualTo(69_900L);
        assertThat(position.marketValueKrw()).isEqualTo(139_800L);
    }

    @Test
    void rejectsNonPositiveFillValues() {
        assertThatThrownBy(() -> portfolioService.applyBuyFill(
                STRATEGY_IDENTITY,
                "TEST",
                0L,
                1L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filledQuantity must be positive.");

        assertThatThrownBy(() -> portfolioService.applySellFill(
                STRATEGY_IDENTITY,
                "TEST",
                1L,
                0L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filledAmountKrw must be positive.");
    }

    @Test
    void resetSnapshotWhenCalledReset() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                100_000L
        );

        PortfolioSnapshot portfolioSnapshot = portfolioService.reset(STRATEGY_IDENTITY);

        assertThat(portfolioSnapshot.cashAmountKrw()).isEqualTo(10_000_000L);
        assertThat(portfolioSnapshot.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(portfolioSnapshot.positions()).isEmpty();
    }

    @Test
    void keepsPortfolioStateIsolatedByStrategyIdentity() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                100_000L
        );

        PortfolioSnapshot otherStrategySnapshot =
                portfolioService.getCurrentSnapshot(OTHER_STRATEGY_IDENTITY);

        assertThat(otherStrategySnapshot.cashAmountKrw()).isEqualTo(10_000_000L);
        assertThat(otherStrategySnapshot.positions()).isEmpty();
        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).positions())
                .hasSize(1);
    }

    @Test
    void keepsPortfolioStateIsolatedByStrategyVersion() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                10L,
                100_000L
        );

        PortfolioSnapshot otherVersionSnapshot =
                portfolioService.getCurrentSnapshot(OTHER_VERSION_IDENTITY);

        assertThat(otherVersionSnapshot.cashAmountKrw()).isEqualTo(10_000_000L);
        assertThat(otherVersionSnapshot.positions()).isEmpty();
    }
}
