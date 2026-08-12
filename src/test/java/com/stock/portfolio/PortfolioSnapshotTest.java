package com.stock.portfolio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioSnapshotTest {

    private final PortfolioSnapshot portfolioSnapshot = new PortfolioSnapshot(
            5_000_000L,
            10_000_000L,
            List.of(
                    samsungPosition(),
                    duplicatedSamsungPosition(),
                    hyundaiPosition()
            )
    );

    private PortfolioPosition samsungPosition() {
        return new PortfolioPosition(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }

    private PortfolioPosition duplicatedSamsungPosition() {
        return new PortfolioPosition(
                "005930",
                5L,
                72_000L,
                360_000L
        );
    }

    private PortfolioPosition hyundaiPosition() {
        return new PortfolioPosition(
                "005380",
                3L,
                200_000L,
                600_000L
        );
    }

    @Test
    void positionMarketValueKrwReturnsSumForMatchingSymbol() {
        long result = portfolioSnapshot.positionMarketValueKrw("005930");

        assertThat(result).isEqualTo(1_060_000L);
    }

    @Test
    void positionMarketValueKrwReturnsZeroForUnknownSymbol() {
        long result = portfolioSnapshot.positionMarketValueKrw("11111");

        assertThat(result).isZero();
    }

    @Test
    void positionMarketValueKrwReturnsZeroForBlankSymbol() {
        long result = portfolioSnapshot.positionMarketValueKrw(" ");

        assertThat(result).isZero();
    }

    @Test
    void positionMarketValueKrwReturnsZeroForNullSymbol() {
        long result = portfolioSnapshot.positionMarketValueKrw(null);

        assertThat(result).isZero();
    }
}
