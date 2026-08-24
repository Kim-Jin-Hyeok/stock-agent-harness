package com.stock.harness.persistence;

import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessPortfolioSnapshotTest {
    @Test
    void fromPreservesPortfolioSnapshotValues() {
        HarnessPortfolioSnapshot snapshot = HarnessPortfolioSnapshot.from(portfolioSnapshot());

        assertThat(snapshot.cashAmountKrw()).isEqualTo(portfolioSnapshot().cashAmountKrw());
        assertThat(snapshot.totalAssetAmountKrw()).isEqualTo(portfolioSnapshot().totalAssetAmountKrw());

        List<HarnessPortfolioPositionSnapshot> positions = snapshot.positions();

        assertThat(positions).hasSize(2);

        assertThat(positions.getFirst().symbol()).isEqualTo("005930");
        assertThat(positions.getFirst().quantity()).isEqualTo(10L);
        assertThat(positions.getFirst().averagePriceKrw()).isEqualTo(70_000L);
        assertThat(positions.getFirst().marketValueKrw()).isEqualTo(700_000L);

        assertThat(positions.getLast().symbol()).isEqualTo("000660");
        assertThat(positions.getLast().quantity()).isEqualTo(5L);
        assertThat(positions.getLast().averagePriceKrw()).isEqualTo(120_000L);
        assertThat(positions.getLast().marketValueKrw()).isEqualTo(600_000L);
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                8_700_000L,
                10_000_000L,
                List.of(
                        samsungPosition(),
                        skHynixPosition()
                )
        );
    }

    private PortfolioPosition samsungPosition() {
        return new PortfolioPosition(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }

    private PortfolioPosition skHynixPosition() {
        return new PortfolioPosition(
                "000660",
                5L,
                120_000L,
                600_000L
        );
    }
}
