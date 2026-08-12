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
            return new RiskCheckResult(
                    RiskCheckStatus.APPROVED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    RiskReasonCode.HOLD_NO_ORDER_REQUIRED,
                    "HOLD decision does not require order risk validation."
            );
        }

        if (decision.symbol() == null || decision.symbol().isBlank()) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    RiskReasonCode.SYMBOL_REQUIRED,
                    "BUY and SELL decisions require a symbol."
            );
        }

        if (decision.quantity() == null || decision.quantity() <= 0) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    RiskReasonCode.QUANTITY_REQUIRED,
                    "BUY and SELL decisions require a positive quantity."
            );
        }

        if (decision.expectedPriceKrw() == null || decision.expectedPriceKrw() <= 0) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    RiskReasonCode.EXPECTED_PRICE_REQUIRED,
                    "BUY and SELL decisions require a positive expectedPriceKrw."
            );
        }

        if (decision.action() == InvestmentAction.BUY) {
            if (decision.estimatedOrderAmountKrw() > portfolioSnapshot.cashAmountKrw()) {
                return new RiskCheckResult(
                        RiskCheckStatus.DENIED,
                        decision.action(),
                        decision.symbol(),
                        decision.quantity(),
                        decision.expectedPriceKrw(),
                        decision.estimatedOrderAmountKrw(),
                        RiskReasonCode.INSUFFICIENT_CASH,
                        "Order amount exceeds available cash."
                );
            }

            long maxOrderAmountKrw = (long) (
                    portfolioSnapshot.totalAssetAmountKrw() * harnessProperties.maxOrderRatio()
            );

            if (decision.estimatedOrderAmountKrw() > maxOrderAmountKrw) {
                return new RiskCheckResult(
                        RiskCheckStatus.DENIED,
                        decision.action(),
                        decision.symbol(),
                        decision.quantity(),
                        decision.expectedPriceKrw(),
                        decision.estimatedOrderAmountKrw(),
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
                return new RiskCheckResult(
                        RiskCheckStatus.DENIED,
                        decision.action(),
                        decision.symbol(),
                        decision.quantity(),
                        decision.expectedPriceKrw(),
                        decision.estimatedOrderAmountKrw(),
                        RiskReasonCode.MAX_POSITION_RATIO_EXCEEDED,
                        "Order amount exceeds max position ratio. estimatedOrderAmountKrw="
                                + decision.estimatedOrderAmountKrw()
                                + ", maxPositionAmountKrw="
                                + maxPositionAmountKrw
                                + ", maxPositionRatio="
                                + harnessProperties.maxPositionRatio()
                );
            }
        }

        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                RiskReasonCode.UNSUPPORTED_ACTION,
                "BUY and SELL are not supported yet."
        );
    }
}
