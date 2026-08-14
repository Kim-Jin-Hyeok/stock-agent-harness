package com.stock.portfolio;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PortfolioService {
    private PortfolioSnapshot currentSnapshot = new PortfolioSnapshot(
            10_000_000L,
            10_000_000L,
            List.of()
    );

    public PortfolioSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    public PortfolioSnapshot applyBuy(String symbol, long quantity, long priceKrw) {
        long buyAmountKrw = quantity * priceKrw;
        List<PortfolioPosition> updatedPositions = new ArrayList<>();
        boolean merged = false;

        for (PortfolioPosition position : currentSnapshot.positions()) {
            if (symbol.equals(position.symbol())) {
                updatedPositions.add(mergePosition(position, quantity, priceKrw));
                merged = true;
            } else {
                updatedPositions.add(position);
            }
        }

        if (!merged) {
            updatedPositions.add(new PortfolioPosition(
                    symbol,
                    quantity,
                    priceKrw,
                    buyAmountKrw
            ));
        }

        currentSnapshot = new PortfolioSnapshot(
                currentSnapshot.cashAmountKrw() - buyAmountKrw,
                currentSnapshot.totalAssetAmountKrw(),
                List.copyOf(updatedPositions)
        );

        return currentSnapshot;
    }

    public PortfolioSnapshot applySell(String symbol, long quantity, long priceKrw) {
        long sellAmountKrw = priceKrw * quantity;
        List<PortfolioPosition> updatedPositions = new ArrayList<>();

        for (PortfolioPosition position : currentSnapshot.positions()) {

            if (symbol.equals(position.symbol())) {
                if (position.quantity() - quantity == 0) {
                    continue;
                }

                long remainingQuantity = position.quantity() - quantity;

                updatedPositions.add(
                        new PortfolioPosition(
                                position.symbol(),
                                remainingQuantity,
                                position.averagePriceKrw(),
                                position.averagePriceKrw() * remainingQuantity
                        )
                );
            } else {
                updatedPositions.add(position);
            }
        }

        long updatedCashAmountKrw = currentSnapshot.cashAmountKrw() + sellAmountKrw;
        long updatedTotalAssetAmountKrw = updatedCashAmountKrw + totalMarketValueKrw(updatedPositions);

        currentSnapshot = new PortfolioSnapshot(
                updatedCashAmountKrw,
                updatedTotalAssetAmountKrw,
                List.copyOf(updatedPositions)
        );

        return currentSnapshot;
    }

    public PortfolioSnapshot reset() {
        currentSnapshot = new PortfolioSnapshot(
                10_000_000L,
                10_000_000L,
                List.of()
        );

        return currentSnapshot;
    }

    private PortfolioPosition mergePosition(
            PortfolioPosition position,
            long quantity,
            long priceKrw
    ) {
        long currentAmountKrw = position.averagePriceKrw() * position.quantity();
        long buyAmountKrw = quantity * priceKrw;
        long mergedQuantity = position.quantity() + quantity;
        long mergedAveragePriceKrw = (currentAmountKrw + buyAmountKrw) / mergedQuantity;
        long mergedMarketValueKrw = position.marketValueKrw() + buyAmountKrw;

        return new PortfolioPosition(
                position.symbol(),
                mergedQuantity,
                mergedAveragePriceKrw,
                mergedMarketValueKrw
        );
    }

    private long totalMarketValueKrw(List<PortfolioPosition> positions) {
        return positions.stream()
                .mapToLong(PortfolioPosition::marketValueKrw)
                .sum();
    }
}
