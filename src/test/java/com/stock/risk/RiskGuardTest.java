package com.stock.risk;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.HarnessProperties;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskGuardTest {

    private final HarnessProperties harnessProperties = new HarnessProperties(
            10,
            0.1,
            0.3
    );

    private final RiskGuard riskGuard = new RiskGuard(harnessProperties);

    private final PortfolioSnapshot portfolioSnapshot = new PortfolioSnapshot(
            10_000_000L,
            10_000_000L,
            List.of()
    );

    private final PortfolioSnapshot portfolioSnapshotWithPosition = new PortfolioSnapshot(
            10_000_000L,
            10_000_000L,
            List.of(
                    new PortfolioPosition(
                            "TEST",
                            25L,
                            100_000L,
                            2_500_000L
                    )
            )
    );

    @Test
    void holdDecisionIsApproved() {
        RiskCheckResult result = riskGuard.validate(
                holdDecision(),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.HOLD_NO_ORDER_REQUIRED);
    }

    @Test
    void missingSymbolIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision(null, 100000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.SYMBOL_REQUIRED);
    }

    @Test
    void blankSymbolIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("", 100000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.SYMBOL_REQUIRED);
    }

    @Test
    void missingOrderAmountIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", null),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.ORDER_AMOUNT_REQUIRED);
    }

    @Test
    void zeroOrderAmountIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 0L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.ORDER_AMOUNT_REQUIRED);
    }

    @Test
    void orderAmountGreaterThanCashIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 100_000_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.INSUFFICIENT_CASH);
    }

    @Test
    void orderAmountGreaterThanMaxOrderRatioIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 2_000_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED);
    }

    @Test
    void validBuyDecisionIsDeniedBecauseBuyIsNotSupportedYet() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 1_000_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.UNSUPPORTED_ACTION);
    }

    @Test
    void orderIsDeniedWhenExpectedPositionAmountExceedsMaxPositionRatio() {
        InvestmentDecision decision = buyDecision("TEST", 1_000_000L);

        RiskCheckResult result = riskGuard.validate(
                decision,
                portfolioSnapshotWithPosition
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.MAX_POSITION_RATIO_EXCEEDED);
    }

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                "Test HOLD decision."
        );
    }

    private InvestmentDecision buyDecision(
            String symbol,
            Long orderAmountKrw
    ) {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                symbol,
                orderAmountKrw,
                "Test BUY decision."
        );
    }
}
