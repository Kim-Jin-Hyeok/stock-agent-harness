package com.stock.trade.cost;

import com.stock.agent.InvestmentAction;
import com.stock.trade.cost.model.TradeCostCalculation;
import com.stock.trade.cost.model.TradeCostModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class TradeCostCalculator {
    private static final BigDecimal ONE = BigDecimal.ONE;

    public TradeCostCalculation calculate(
            TradeCostModel costModel,
            InvestmentAction action,
            long quantity,
            long referencePriceKrw
    ) {
        Objects.requireNonNull(costModel, "costModel must not be null.");
        Objects.requireNonNull(action, "action must not be null.");
        if (action == InvestmentAction.HOLD) {
            throw new IllegalArgumentException(
                    "Trade cost calculation requires BUY or SELL action."
            );
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive."
            );
        }
        if (referencePriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "referencePriceKrw must be positive."
            );
        }

        long executionPriceKrw = executionPriceKrw(
                costModel,
                action,
                referencePriceKrw
        );
        long referenceOrderAmountKrw = Math.multiplyExact(
                quantity,
                referencePriceKrw
        );
        long executionOrderAmountKrw = Math.multiplyExact(
                quantity,
                executionPriceKrw
        );
        long commissionAmountKrw = rateAmount(
                executionOrderAmountKrw,
                commissionRate(costModel, action)
        );
        long taxAmountKrw = action == InvestmentAction.SELL
                ? rateAmount(
                        executionOrderAmountKrw,
                        costModel.sellTaxRate()
                )
                : 0L;
        long slippageAmountKrw = action == InvestmentAction.BUY
                ? Math.subtractExact(
                        executionOrderAmountKrw,
                        referenceOrderAmountKrw
                )
                : Math.subtractExact(
                        referenceOrderAmountKrw,
                        executionOrderAmountKrw
                );
        long totalCostAmountKrw = Math.addExact(
                slippageAmountKrw,
                Math.addExact(commissionAmountKrw, taxAmountKrw)
        );
        long settlementAmountKrw = settlementAmountKrw(
                action,
                executionOrderAmountKrw,
                commissionAmountKrw,
                taxAmountKrw
        );

        return new TradeCostCalculation(
                costModel,
                action,
                quantity,
                referencePriceKrw,
                executionPriceKrw,
                referenceOrderAmountKrw,
                executionOrderAmountKrw,
                commissionAmountKrw,
                taxAmountKrw,
                slippageAmountKrw,
                totalCostAmountKrw,
                settlementAmountKrw
        );
    }

    private long executionPriceKrw(
            TradeCostModel costModel,
            InvestmentAction action,
            long referencePriceKrw
    ) {
        BigDecimal slippageRate = action == InvestmentAction.BUY
                ? costModel.buySlippageRate()
                : costModel.sellSlippageRate();
        BigDecimal multiplier = action == InvestmentAction.BUY
                ? ONE.add(slippageRate)
                : ONE.subtract(slippageRate);
        RoundingMode roundingMode = action == InvestmentAction.BUY
                ? RoundingMode.CEILING
                : RoundingMode.FLOOR;
        long executionPriceKrw = BigDecimal
                .valueOf(referencePriceKrw)
                .multiply(multiplier)
                .setScale(0, roundingMode)
                .longValueExact();
        if (executionPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "Calculated executionPriceKrw must be positive."
            );
        }
        return executionPriceKrw;
    }

    private BigDecimal commissionRate(
            TradeCostModel costModel,
            InvestmentAction action
    ) {
        return action == InvestmentAction.BUY
                ? costModel.buyCommissionRate()
                : costModel.sellCommissionRate();
    }

    private long rateAmount(long amountKrw, BigDecimal rate) {
        return BigDecimal.valueOf(amountKrw)
                .multiply(rate)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    private long settlementAmountKrw(
            InvestmentAction action,
            long executionOrderAmountKrw,
            long commissionAmountKrw,
            long taxAmountKrw
    ) {
        long feeAndTaxAmountKrw = Math.addExact(
                commissionAmountKrw,
                taxAmountKrw
        );
        long settlementAmountKrw = action == InvestmentAction.BUY
                ? Math.addExact(
                        executionOrderAmountKrw,
                        feeAndTaxAmountKrw
                )
                : Math.subtractExact(
                        executionOrderAmountKrw,
                        feeAndTaxAmountKrw
                );
        if (settlementAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "Calculated settlementAmountKrw must be positive."
            );
        }
        return settlementAmountKrw;
    }
}
