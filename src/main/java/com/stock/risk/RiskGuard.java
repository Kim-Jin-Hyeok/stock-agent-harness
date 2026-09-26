package com.stock.risk;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class RiskGuard {
    private final OrderQuantityCapacityCalculator capacityCalculator;

    public RiskGuard(OrderQuantityCapacityCalculator capacityCalculator) {
        this.capacityCalculator = Objects.requireNonNull(
                capacityCalculator,
                "capacityCalculator must not be null."
        );
    }

    public RiskCheckResult validate(
            InvestmentDecision decision,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        Objects.requireNonNull(marketSnapshot, "marketSnapshot must not be null.");

        if (decision.action() == InvestmentAction.HOLD) {
            return approved(
                    decision,
                    RiskReasonCode.HOLD_NO_ORDER_REQUIRED,
                    "HOLD decision does not require order risk validation."
            );
        }

        Optional<RiskCheckResult> commonValidateResult = validateCommonOrderFields(decision);

        if (commonValidateResult.isPresent()) {
            return commonValidateResult.get();
        }

        if (!marketSnapshot.marketOpen()) {
            return denied(
                    decision,
                    RiskReasonCode.MARKET_CLOSED,
                    "Order is not allowed while market is closed. market="
                            + marketSnapshot.market()
            );
        }

        if (decision.action() == InvestmentAction.BUY) {
            return validateBuy(decision, portfolioSnapshot);
        }

        if (decision.action() == InvestmentAction.SELL) {
            return validateSell(decision, portfolioSnapshot);
        }

        return approved(
                decision,
                RiskReasonCode.RISK_APPROVED,
                "Risk validation approved."
        );
    }

    private RiskCheckResult validateBuy(InvestmentDecision decision, PortfolioSnapshot portfolioSnapshot) {
        OrderQuantityCapacity capacity = capacityCalculator.calculate(
                decision.action(),
                decision.symbol(),
                decision.expectedPriceKrw(),
                portfolioSnapshot
        );

        if (decision.quantity() > capacity.maxAffordableQuantity()) {
            return denied(
                    decision,
                    RiskReasonCode.INSUFFICIENT_CASH,
                    "Order amount exceeds available cash. decisionQuantity="
                            + decision.quantity()
                            + ", maxAffordableQuantity="
                            + capacity.maxAffordableQuantity()
            );
        }

        if (decision.quantity() > capacity.maxOrderRatioQuantity()) {
            return denied(
                    decision,
                    RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED,
                    "Order amount exceeds max order ratio. decisionQuantity="
                            + decision.quantity()
                            + ", maxOrderRatioQuantity="
                            + capacity.maxOrderRatioQuantity()
            );
        }

        if (decision.quantity() > capacity.maxPositionRatioQuantity()) {
            return denied(
                    decision,
                    RiskReasonCode.MAX_POSITION_RATIO_EXCEEDED,
                    "Order amount exceeds max position ratio. decisionQuantity="
                            + decision.quantity()
                            + ", maxPositionRatioQuantity="
                            + capacity.maxPositionRatioQuantity()
            );
        }

        return approved(
                decision,
                RiskReasonCode.RISK_APPROVED,
                "Risk validation approved."
        );
    }

    private RiskCheckResult validateSell(InvestmentDecision decision, PortfolioSnapshot portfolioSnapshot) {
        OrderQuantityCapacity capacity = capacityCalculator.calculate(
                decision.action(),
                decision.symbol(),
                decision.expectedPriceKrw(),
                portfolioSnapshot
        );

        if (!capacity.canOrder()) {
            return denied(
                    decision,
                    RiskReasonCode.POSITION_NOT_FOUND,
                    "Position not found."
            );
        }

        if (decision.quantity() > capacity.maxAllowedQuantity()) {
            return denied(
                    decision,
                    RiskReasonCode.SELL_QUANTITY_EXCEEDS_POSITION,
                    "Sell quantity exceeds position. decisionQuantity="
                            + decision.quantity()
                            + ", positionQuantity="
                            + capacity.currentPositionQuantity()
            );
        }

        return approved(
                decision,
                RiskReasonCode.RISK_APPROVED,
                "Risk validation approved."
        );
    }

    private RiskCheckResult denied(InvestmentDecision decision, RiskReasonCode reasonCode, String reason) {
        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                reasonCode,
                reason
        );
    }

    private RiskCheckResult approved(InvestmentDecision decision, RiskReasonCode reasonCode, String reason) {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                reasonCode,
                reason
        );
    }

    private Optional<RiskCheckResult> validateCommonOrderFields(InvestmentDecision decision) {
        if (decision.symbol() == null || decision.symbol().isBlank()) {
            return Optional.of(denied(
                    decision,
                    RiskReasonCode.SYMBOL_REQUIRED,
                    "BUY and SELL decisions require a symbol."
            ));
        }

        if (decision.quantity() == null || decision.quantity() <= 0) {
            return Optional.of(denied(
                    decision,
                    RiskReasonCode.QUANTITY_REQUIRED,
                    "BUY and SELL decisions require a positive quantity."
            ));
        }

        if (decision.expectedPriceKrw() == null || decision.expectedPriceKrw() <= 0) {
            return Optional.of(denied(
                    decision,
                    RiskReasonCode.EXPECTED_PRICE_REQUIRED,
                    "BUY and SELL decisions require a positive expectedPriceKrw."
            ));
        }

        return Optional.empty();
    }
}
