package com.stock.portfolio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioServiceTest {

    private final PortfolioService portfolioService = new PortfolioService();

    @Test
    void applyBuyAddsNewPosition() {
        PortfolioSnapshot result = portfolioService.applyBuy(
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
                "TEST",
                10L,
                100_000L
        );

        PortfolioSnapshot result = portfolioService.applyBuy(
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
}
