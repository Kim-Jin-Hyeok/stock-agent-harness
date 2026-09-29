package com.stock.risk.capacity;

import com.stock.agent.InvestmentAction;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.risk.RiskProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class OrderQuantityCapacityCalculator {
    private static final int RATIO_SCALE = 6;

    private final RiskProperties riskProperties;

    public OrderQuantityCapacityCalculator(RiskProperties riskProperties) {
        this.riskProperties = Objects.requireNonNull(
                riskProperties,
                "riskProperties must not be null."
        );
    }

    public OrderQuantityCapacity calculate(
            InvestmentAction action,
            String symbol,
            long currentPriceKrw,
            PortfolioSnapshot portfolioSnapshot
    ) {
        Objects.requireNonNull(action, "action must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (currentPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "currentPriceKrw must be positive."
            );
        }
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );

        long currentPositionQuantity =
                portfolioSnapshot.positionQuantity(symbol);
        return calculate(
                action,
                symbol,
                currentPriceKrw,
                portfolioSnapshot.cashAmountKrw(),
                portfolioSnapshot.totalAssetAmountKrw(),
                currentPositionQuantity,
                portfolioSnapshot.positionMarketValueKrw(symbol)
        );
    }

    public OrderQuantityCapacity calculate(
            InvestmentAction action,
            String symbol,
            long currentPriceKrw,
            PortfolioValuationSnapshot valuationSnapshot
    ) {
        Objects.requireNonNull(action, "action must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (currentPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "currentPriceKrw must be positive."
            );
        }
        Objects.requireNonNull(
                valuationSnapshot,
                "valuationSnapshot must not be null."
        );

        return calculate(
                action,
                symbol,
                currentPriceKrw,
                valuationSnapshot.cashAmountKrw(),
                valuationSnapshot.totalAssetAmountKrw(),
                valuationSnapshot.positionQuantity(symbol),
                valuationSnapshot.positionEvaluationAmountKrw(symbol)
        );
    }

    private OrderQuantityCapacity calculate(
            InvestmentAction action,
            String symbol,
            long currentPriceKrw,
            long cashAmountKrw,
            long totalAssetAmountKrw,
            long currentPositionQuantity,
            long currentPositionAmountKrw
    ) {
        BigDecimal oneSharePortfolioRatio = oneSharePortfolioRatio(
                currentPriceKrw,
                totalAssetAmountKrw
        );

        return switch (action) {
            case BUY -> buyCapacity(
                    symbol,
                    currentPriceKrw,
                    currentPositionQuantity,
                    oneSharePortfolioRatio,
                    cashAmountKrw,
                    totalAssetAmountKrw,
                    currentPositionAmountKrw
            );
            case SELL -> new OrderQuantityCapacity(
                    action,
                    symbol,
                    currentPriceKrw,
                    currentPositionQuantity,
                    0,
                    0,
                    0,
                    currentPositionQuantity,
                    oneSharePortfolioRatio
            );
            case HOLD -> new OrderQuantityCapacity(
                    action,
                    symbol,
                    currentPriceKrw,
                    currentPositionQuantity,
                    0,
                    0,
                    0,
                    0,
                    oneSharePortfolioRatio
            );
        };
    }

    private OrderQuantityCapacity buyCapacity(
            String symbol,
            long currentPriceKrw,
            long currentPositionQuantity,
            BigDecimal oneSharePortfolioRatio,
            long cashAmountKrw,
            long totalAssetAmountKrw,
            long currentPositionAmountKrw
    ) {
        long maxAffordableQuantity = nonNegativeAmount(
                cashAmountKrw
        ) / currentPriceKrw;
        long maxOrderAmountKrw = ratioAmount(
                totalAssetAmountKrw,
                riskProperties.maxOrderRatio()
        );
        long maxOrderRatioQuantity =
                maxOrderAmountKrw / currentPriceKrw;
        long maxPositionAmountKrw = ratioAmount(
                totalAssetAmountKrw,
                riskProperties.maxPositionRatio()
        );
        long remainingPositionAmountKrw = Math.max(
                0,
                maxPositionAmountKrw
                        - currentPositionAmountKrw
        );
        long maxPositionRatioQuantity =
                remainingPositionAmountKrw / currentPriceKrw;
        long maxAllowedQuantity = Math.min(
                maxAffordableQuantity,
                Math.min(
                        maxOrderRatioQuantity,
                        maxPositionRatioQuantity
                )
        );

        return new OrderQuantityCapacity(
                InvestmentAction.BUY,
                symbol,
                currentPriceKrw,
                currentPositionQuantity,
                maxAffordableQuantity,
                maxOrderRatioQuantity,
                maxPositionRatioQuantity,
                maxAllowedQuantity,
                oneSharePortfolioRatio
        );
    }

    private long ratioAmount(long totalAssetAmountKrw, double ratio) {
        if (totalAssetAmountKrw <= 0 || ratio <= 0) {
            return 0;
        }
        return (long) (totalAssetAmountKrw * ratio);
    }

    private long nonNegativeAmount(long amountKrw) {
        return Math.max(0, amountKrw);
    }

    private BigDecimal oneSharePortfolioRatio(
            long currentPriceKrw,
            long totalAssetAmountKrw
    ) {
        if (totalAssetAmountKrw <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(currentPriceKrw).divide(
                BigDecimal.valueOf(totalAssetAmountKrw),
                RATIO_SCALE,
                RoundingMode.HALF_UP
        );
    }
}
