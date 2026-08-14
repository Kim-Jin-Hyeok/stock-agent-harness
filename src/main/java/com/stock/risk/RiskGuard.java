package com.stock.risk;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.HarnessProperties;
import com.stock.portfolio.PortfolioSnapshot;
import org.springframework.stereotype.Component;

@Component
public class RiskGuard {

    private final HarnessProperties harnessProperties;

    public RiskGuard(HarnessProperties harnessProperties) {
        this.harnessProperties = harnessProperties;
    }

    public RiskCheckResult validate(
            InvestmentDecision decision,
            PortfolioSnapshot portfolioSnapshot
    ) {
        if (decision.action() == InvestmentAction.HOLD) {
            return approved(
                    decision,
                    RiskReasonCode.HOLD_NO_ORDER_REQUIRED,
                    "HOLD decision does not require order risk validation."
            );
        }

        if (decision.symbol() == null || decision.symbol().isBlank()) {
            return denied(
                    decision,
                    RiskReasonCode.SYMBOL_REQUIRED,
                    "BUY and SELL decisions require a symbol."
            );
        }

        if (decision.quantity() == null || decision.quantity() <= 0) {
            return denied(
                    decision,
                    RiskReasonCode.QUANTITY_REQUIRED,
                    "BUY and SELL decisions require a positive quantity."
            );
        }

        if (decision.expectedPriceKrw() == null || decision.expectedPriceKrw() <= 0) {
            return denied(
                    decision,
                    RiskReasonCode.EXPECTED_PRICE_REQUIRED,
                    "BUY and SELL decisions require a positive expectedPriceKrw."
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
        if (decision.estimatedOrderAmountKrw() > portfolioSnapshot.cashAmountKrw()) {
            return denied(
                    decision,
                    RiskReasonCode.INSUFFICIENT_CASH,
                    "Order amount exceeds available cash."
            );
        }

        long maxOrderAmountKrw = (long) (
                portfolioSnapshot.totalAssetAmountKrw() * harnessProperties.maxOrderRatio()
        );

        if (decision.estimatedOrderAmountKrw() > maxOrderAmountKrw) {
            return denied(
                    decision,
                    RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED,
                    "Order amount exceeds max order ratio. estimatedOrderAmountKrw="
                            + decision.estimatedOrderAmountKrw()
                            + ", maxOrderAmountKrw="
                            + maxOrderAmountKrw
                            + ", maxOrderRatio="
                            + harnessProperties.maxOrderRatio()
            );
        }

        long currentPositionMarketValueKrw =
                portfolioSnapshot.positionMarketValueKrw(decision.symbol());
        long expectedPositionAmountKrw =
                currentPositionMarketValueKrw + decision.estimatedOrderAmountKrw();
        long maxPositionAmountKrw = (long) (
                portfolioSnapshot.totalAssetAmountKrw() * harnessProperties.maxPositionRatio()
        );

        if (expectedPositionAmountKrw > maxPositionAmountKrw) {
            return denied(
                    decision,
                    RiskReasonCode.MAX_POSITION_RATIO_EXCEEDED,
                    "Order amount exceeds max position ratio. estimatedOrderAmountKrw="
                            + decision.estimatedOrderAmountKrw()
                            + ", maxPositionAmountKrw="
                            + maxPositionAmountKrw
                            + ", maxPositionRatio="
                            + harnessProperties.maxPositionRatio()
            );
        }

        return approved(
                decision,
                RiskReasonCode.RISK_APPROVED,
                "Risk validation approved."
        );
    }

    private RiskCheckResult validateSell(InvestmentDecision decision, PortfolioSnapshot portfolioSnapshot) {
        if (portfolioSnapshot.positionQuantity(decision.symbol()) == 0) {
            return denied(
                    decision,
                    RiskReasonCode.POSITION_NOT_FOUND,
                    "Position not found."
            );
        }

        if (decision.quantity() > portfolioSnapshot.positionQuantity(decision.symbol())) {
            return denied(
                    decision,
                    RiskReasonCode.SELL_QUANTITY_EXCEEDS_POSITION,
                    "Sell quantity exceeds position. decisionQuantity="
                            + decision.quantity()
                            + ", positionQuantity="
                            + portfolioSnapshot.positionQuantity(decision.symbol())
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
}
