package com.stock.portfolio;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortfolioSnapshotStore {
    private PortfolioSnapshot currentSnapshot = new PortfolioSnapshot(
            10_000_000L,
            10_000_000L,
            List.of()
    );

    public PortfolioSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    public void update(PortfolioSnapshot snapshot) {
        currentSnapshot = snapshot;
    }

    public PortfolioSnapshot reset() {
        currentSnapshot = new PortfolioSnapshot(
                10_000_000L,
                10_000_000L,
                List.of()
        );

        return currentSnapshot;
    }
}
