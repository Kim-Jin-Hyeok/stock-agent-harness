package com.stock.agent.decision.swing.v1.quantity.policy;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisStatus;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class SwingV1OrderQuantityPolicy {
    private static final String STRATEGY_ID = "SWING_V1";
    private static final int STRATEGY_VERSION = 1;
    private static final BigDecimal ORDER_RISK_RATIO =
            new BigDecimal("0.005");
    private static final BigDecimal INITIAL_STOP_ATR_MULTIPLIER =
            new BigDecimal("2.0");
    private static final BigDecimal LONG_MAX_VALUE =
            BigDecimal.valueOf(Long.MAX_VALUE);

    private final OrderQuantityCapacityCalculator capacityCalculator;

    public SwingV1OrderQuantityPolicy(
            OrderQuantityCapacityCalculator capacityCalculator
    ) {
        this.capacityCalculator = Objects.requireNonNull(
                capacityCalculator,
                "capacityCalculator must not be null."
        );
    }

    public SwingV1OrderQuantityResult calculate(
            SwingV1ActionPolicyResult actionResult,
            SwingTechnicalAnalysisResult analysis,
            CurrentPriceSnapshot currentPriceSnapshot,
            PortfolioValuationSnapshot valuationSnapshot
    ) {
        Objects.requireNonNull(
                actionResult,
                "actionResult must not be null."
        );
        Objects.requireNonNull(analysis, "analysis must not be null.");
        Objects.requireNonNull(
                currentPriceSnapshot,
                "currentPriceSnapshot must not be null."
        );
        Objects.requireNonNull(
                valuationSnapshot,
                "valuationSnapshot must not be null."
        );

        validateStrategyIdentity(analysis.strategyIdentity());
        validateCurrentPrice(analysis.symbol(), currentPriceSnapshot);

        InvestmentAction action = actionResult.action();
        if (action != InvestmentAction.HOLD
                && analysis.status()
                != SwingTechnicalAnalysisStatus.ANALYZED) {
            throw new IllegalArgumentException(
                    "Order quantity requires analyzed swing technical data."
            );
        }
        OrderQuantityCapacity capacity = capacityCalculator.calculate(
                action,
                analysis.symbol(),
                currentPriceSnapshot.priceKrw(),
                valuationSnapshot
        );

        return switch (action) {
            case BUY -> calculateBuy(
                    analysis,
                    valuationSnapshot,
                    capacity
            );
            case SELL -> noRiskSizingResult(
                    action,
                    analysis.symbol(),
                    capacity,
                    capacity.maxAllowedQuantity() > 0
                            ? SwingV1OrderQuantityReasonCode.FULL_POSITION_EXIT
                            : SwingV1OrderQuantityReasonCode
                                    .ORDER_CAPACITY_UNAVAILABLE,
                    "Use the full current position for SWING_V1 exit."
            );
            case HOLD -> noRiskSizingResult(
                    action,
                    analysis.symbol(),
                    capacity,
                    SwingV1OrderQuantityReasonCode.HOLD_NO_ORDER,
                    "HOLD action does not create an order."
            );
        };
    }

    private SwingV1OrderQuantityResult calculateBuy(
            SwingTechnicalAnalysisResult analysis,
            PortfolioValuationSnapshot valuationSnapshot,
            OrderQuantityCapacity capacity
    ) {
        if (analysis.status() != SwingTechnicalAnalysisStatus.ANALYZED) {
            throw new IllegalArgumentException(
                    "BUY quantity requires analyzed swing technical data."
            );
        }

        long riskBudgetKrw = riskBudgetKrw(
                valuationSnapshot.totalAssetAmountKrw()
        );
        BigDecimal riskPerShareKrw = analysis
                .averageTrueRangeAnalysis()
                .averageTrueRange()
                .averageTrueRangeKrw()
                .multiply(INITIAL_STOP_ATR_MULTIPLIER);
        long riskBasedQuantity = riskBasedQuantity(
                riskBudgetKrw,
                riskPerShareKrw
        );
        long finalQuantity = Math.min(
                riskBasedQuantity,
                capacity.maxAllowedQuantity()
        );
        SwingV1OrderQuantityReasonCode reasonCode = buyReasonCode(
                riskPerShareKrw,
                riskBasedQuantity,
                capacity,
                finalQuantity
        );

        return new SwingV1OrderQuantityResult(
                InvestmentAction.BUY,
                analysis.symbol(),
                riskBudgetKrw,
                riskPerShareKrw,
                riskBasedQuantity,
                capacity,
                finalQuantity,
                reasonCode,
                "Calculated SWING_V1 BUY quantity. riskBudgetKrw="
                        + riskBudgetKrw
                        + ", riskPerShareKrw="
                        + riskPerShareKrw
                        + ", riskBasedQuantity="
                        + riskBasedQuantity
                        + ", maxAllowedQuantity="
                        + capacity.maxAllowedQuantity()
                        + ", finalQuantity="
                        + finalQuantity
        );
    }

    private SwingV1OrderQuantityResult noRiskSizingResult(
            InvestmentAction action,
            String symbol,
            OrderQuantityCapacity capacity,
            SwingV1OrderQuantityReasonCode reasonCode,
            String reason
    ) {
        return new SwingV1OrderQuantityResult(
                action,
                symbol,
                0L,
                BigDecimal.ZERO,
                0L,
                capacity,
                capacity.maxAllowedQuantity(),
                reasonCode,
                reason
        );
    }

    private long riskBudgetKrw(long totalAssetAmountKrw) {
        return BigDecimal.valueOf(totalAssetAmountKrw)
                .multiply(ORDER_RISK_RATIO)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    private long riskBasedQuantity(
            long riskBudgetKrw,
            BigDecimal riskPerShareKrw
    ) {
        if (riskBudgetKrw == 0L || riskPerShareKrw.signum() == 0) {
            return 0L;
        }
        BigDecimal calculatedQuantity = BigDecimal
                .valueOf(riskBudgetKrw)
                .divideToIntegralValue(riskPerShareKrw)
                .min(LONG_MAX_VALUE);
        return calculatedQuantity.longValueExact();
    }

    private SwingV1OrderQuantityReasonCode buyReasonCode(
            BigDecimal riskPerShareKrw,
            long riskBasedQuantity,
            OrderQuantityCapacity capacity,
            long finalQuantity
    ) {
        if (riskPerShareKrw.signum() == 0) {
            return SwingV1OrderQuantityReasonCode.ATR_RISK_UNAVAILABLE;
        }
        if (riskBasedQuantity == 0L) {
            return SwingV1OrderQuantityReasonCode
                    .ATR_RISK_BUDGET_INSUFFICIENT;
        }
        if (capacity.maxAllowedQuantity() == 0L) {
            return SwingV1OrderQuantityReasonCode
                    .ORDER_CAPACITY_UNAVAILABLE;
        }
        if (finalQuantity == riskBasedQuantity) {
            return SwingV1OrderQuantityReasonCode.ATR_RISK_LIMITED_BUY;
        }
        return SwingV1OrderQuantityReasonCode
                .HARNESS_CAPACITY_LIMITED_BUY;
    }

    private void validateStrategyIdentity(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        if (!STRATEGY_ID.equals(strategyIdentity.strategyId())
                || strategyIdentity.strategyVersion() != STRATEGY_VERSION
                || strategyIdentity.horizon() != InvestmentHorizon.SWING) {
            throw new IllegalArgumentException(
                    "analysis must use SWING_V1 strategy identity."
            );
        }
    }

    private void validateCurrentPrice(
            String symbol,
            CurrentPriceSnapshot currentPriceSnapshot
    ) {
        if (!symbol.equals(currentPriceSnapshot.symbol())) {
            throw new IllegalArgumentException(
                    "Current price symbol must match analysis symbol."
            );
        }
        if (currentPriceSnapshot.priceKrw() <= 0) {
            throw new IllegalArgumentException(
                    "Current price must be positive."
            );
        }
        Objects.requireNonNull(
                currentPriceSnapshot.observedAt(),
                "currentPriceSnapshot.observedAt must not be null."
        );
    }
}
