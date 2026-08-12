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
                    decision.orderAmountKrw(),
                    RiskReasonCode.HOLD_NO_ORDER_REQUIRED,
                    "HOLD decision does not require order risk validation."
            );
        }

        if (decision.symbol() == null || decision.symbol().isBlank()) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    RiskReasonCode.SYMBOL_REQUIRED,
                    "BUY and SELL decisions require a symbol."
            );
        }

        if (decision.orderAmountKrw() == null || decision.orderAmountKrw() <= 0) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    RiskReasonCode.ORDER_AMOUNT_REQUIRED,
                    "BUY and SELL decisions require a positive order amount."
            );
        }

        if (decision.orderAmountKrw() > portfolioSnapshot.cashAmountKrw()) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    RiskReasonCode.INSUFFICIENT_CASH,
                    "Order amount exceeds available cash."
            );
        }

        long maxOrderAmountKrw = (long) (
                portfolioSnapshot.totalAssetAmountKrw() * harnessProperties.maxOrderRatio()
        );

        if (decision.orderAmountKrw() > maxOrderAmountKrw) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED,
                    "Order amount exceeds max order ratio. orderAmountKrw="
                            + decision.orderAmountKrw()
                            + ", maxOrderAmountKrw="
                            + maxOrderAmountKrw
                            + ", maxOrderRatio="
                            + harnessProperties.maxOrderRatio()
            );
        }

        long currentPositionMarketValueKrw =
                portfolioSnapshot.positionMarketValueKrw(decision.symbol());
        long expectedPositionAmountKrw =
                currentPositionMarketValueKrw + decision.orderAmountKrw();
        long maxPositionAmountKrw = (long) (
                portfolioSnapshot.totalAssetAmountKrw() * harnessProperties.maxPositionRatio()
        );

        if (expectedPositionAmountKrw > maxPositionAmountKrw) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    RiskReasonCode.MAX_POSITION_RATIO_EXCEEDED,
                    "Order amount exceeds max position ratio. orderAmountKrw="
                            + decision.orderAmountKrw()
                            + ", maxPositionAmountKrw="
                            + maxPositionAmountKrw
                            + ", maxPositionRatio="
                            + harnessProperties.maxPositionRatio()
            );
        }

        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                decision.action(),
                decision.symbol(),
                decision.orderAmountKrw(),
                RiskReasonCode.UNSUPPORTED_ACTION,
                "BUY and SELL are not supported yet."
        );
    }
}
