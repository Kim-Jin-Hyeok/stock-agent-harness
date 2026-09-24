package com.stock.portfolio;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioSnapshotStore store;

    public PortfolioSnapshot getCurrentSnapshot(InvestmentStrategyIdentity strategyIdentity) {
        return store.getCurrentSnapshot(strategyIdentity);
    }

    public PortfolioSnapshot applyBuy(
            InvestmentStrategyIdentity strategyIdentity,
            String symbol,
            long quantity,
            long priceKrw
    ) {
        return applyBuyFill(
                strategyIdentity,
                symbol,
                quantity,
                Math.multiplyExact(quantity, priceKrw)
        );
    }

    public PortfolioSnapshot applyBuyFill(
            InvestmentStrategyIdentity strategyIdentity,
            String symbol,
            long filledQuantity,
            long filledAmountKrw
    ) {
        validateFill(filledQuantity, filledAmountKrw);
        PortfolioSnapshot currentSnapshot = store.getCurrentSnapshot(strategyIdentity);

        List<PortfolioPosition> updatedPositions = new ArrayList<>();
        boolean merged = false;

        for (PortfolioPosition position : currentSnapshot.positions()) {
            if (symbol.equals(position.symbol())) {
                updatedPositions.add(mergePosition(
                        position,
                        filledQuantity,
                        filledAmountKrw
                ));
                merged = true;
            } else {
                updatedPositions.add(position);
            }
        }

        if (!merged) {
            updatedPositions.add(new PortfolioPosition(
                    symbol,
                    filledQuantity,
                    filledAmountKrw / filledQuantity,
                    filledAmountKrw
            ));
        }

        currentSnapshot = new PortfolioSnapshot(
                currentSnapshot.cashAmountKrw() - filledAmountKrw,
                currentSnapshot.totalAssetAmountKrw(),
                List.copyOf(updatedPositions)
        );

        store.update(strategyIdentity, currentSnapshot);

        return currentSnapshot;
    }

    public PortfolioSnapshot applySell(
            InvestmentStrategyIdentity strategyIdentity,
            String symbol,
            long quantity,
            long priceKrw
    ) {
        return applySellFill(
                strategyIdentity,
                symbol,
                quantity,
                Math.multiplyExact(quantity, priceKrw)
        );
    }

    public PortfolioSnapshot applySellFill(
            InvestmentStrategyIdentity strategyIdentity,
            String symbol,
            long filledQuantity,
            long filledAmountKrw
    ) {
        validateFill(filledQuantity, filledAmountKrw);
        PortfolioSnapshot currentSnapshot = store.getCurrentSnapshot(strategyIdentity);

        List<PortfolioPosition> updatedPositions = new ArrayList<>();

        for (PortfolioPosition position : currentSnapshot.positions()) {

            if (symbol.equals(position.symbol())) {
                if (position.quantity() - filledQuantity == 0) {
                    continue;
                }

                long remainingQuantity =
                        position.quantity() - filledQuantity;

                updatedPositions.add(
                        new PortfolioPosition(
                                position.symbol(),
                                remainingQuantity,
                                position.averagePriceKrw(),
                                remainingMarketValueKrw(
                                        position,
                                        remainingQuantity
                                )
                        )
                );
            } else {
                updatedPositions.add(position);
            }
        }

        long updatedCashAmountKrw =
                currentSnapshot.cashAmountKrw() + filledAmountKrw;
        long updatedTotalAssetAmountKrw = updatedCashAmountKrw + totalMarketValueKrw(updatedPositions);

        currentSnapshot = new PortfolioSnapshot(
                updatedCashAmountKrw,
                updatedTotalAssetAmountKrw,
                List.copyOf(updatedPositions)
        );

        store.update(strategyIdentity, currentSnapshot);

        return currentSnapshot;
    }

    public PortfolioSnapshot reset(InvestmentStrategyIdentity strategyIdentity) {
        return store.reset(strategyIdentity);
    }

    public void resetAll() {
        store.resetAll();
    }

    private PortfolioPosition mergePosition(
            PortfolioPosition position,
            long filledQuantity,
            long filledAmountKrw
    ) {
        long mergedQuantity = position.quantity() + filledQuantity;
        long mergedMarketValueKrw =
                position.marketValueKrw() + filledAmountKrw;
        long mergedAveragePriceKrw =
                mergedMarketValueKrw / mergedQuantity;

        return new PortfolioPosition(
                position.symbol(),
                mergedQuantity,
                mergedAveragePriceKrw,
                mergedMarketValueKrw
        );
    }

    private long remainingMarketValueKrw(
            PortfolioPosition position,
            long remainingQuantity
    ) {
        return position.marketValueKrw()
                * remainingQuantity
                / position.quantity();
    }

    private void validateFill(long filledQuantity, long filledAmountKrw) {
        if (filledQuantity <= 0) {
            throw new IllegalArgumentException(
                    "filledQuantity must be positive."
            );
        }
        if (filledAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "filledAmountKrw must be positive."
            );
        }
    }

    private long totalMarketValueKrw(List<PortfolioPosition> positions) {
        return positions.stream()
                .mapToLong(PortfolioPosition::marketValueKrw)
                .sum();
    }
}
