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
                buyDecision(null, 1L, 100_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.SYMBOL_REQUIRED);
    }

    @Test
    void blankSymbolIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("", 1L, 100_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.SYMBOL_REQUIRED);
    }

    @Test
    void missingQuantityIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", null, 100_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.QUANTITY_REQUIRED);
    }

    @Test
    void zeroQuantityIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 0L, 100_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.QUANTITY_REQUIRED);
    }

    @Test
    void missingExpectedPriceIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 1L, null),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.EXPECTED_PRICE_REQUIRED);
    }

    @Test
    void zeroExpectedPriceIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 1L, 0L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.EXPECTED_PRICE_REQUIRED);
    }

    @Test
    void estimatedOrderAmountGreaterThanCashIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 1_000L, 100_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.INSUFFICIENT_CASH);
    }

    @Test
    void estimatedOrderAmountGreaterThanMaxOrderRatioIsDenied() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 20L, 100_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED);
    }

    @Test
    void validBuyDecisionIsDeniedBecauseBuyIsNotSupportedYet() {
        RiskCheckResult result = riskGuard.validate(
                buyDecision("TEST", 10L, 100_000L),
                portfolioSnapshot
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.UNSUPPORTED_ACTION);
    }

    @Test
    void orderIsDeniedWhenExpectedPositionAmountExceedsMaxPositionRatio() {
        InvestmentDecision decision = buyDecision("TEST", 10L, 100_000L);

        RiskCheckResult result = riskGuard.validate(
                decision,
                portfolioSnapshotWithPosition
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.MAX_POSITION_RATIO_EXCEEDED);
    }

    @Test
    void sellDecisionIsDeniedWhenPositionNotFound() {
        InvestmentDecision decision = sellDecision("ABCD", 10L, 100L);

        RiskCheckResult result = riskGuard.validate(
                decision,
                portfolioSnapshotWithPosition
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.POSITION_NOT_FOUND);
    }

    @Test
    void sellDecisionIsDeniedWhenQuantityExceedsPosition() {
        InvestmentDecision decision = sellDecision("TEST", 30L, 100L);

        RiskCheckResult result = riskGuard.validate(
                decision,
                portfolioSnapshotWithPosition
        );

        assertThat(result.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo(RiskReasonCode.SELL_QUANTITY_EXCEEDS_POSITION);
    }

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Test HOLD decision."
        );
    }

    private InvestmentDecision buyDecision(
            String symbol,
            Long quantity,
            Long expectedPriceKrw
    ) {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                symbol,
                quantity,
                expectedPriceKrw,
                "Test BUY decision."
        );
    }

    private InvestmentDecision sellDecision(
            String symbol,
            Long quantity,
            Long expectedPriceKrw
    ) {
        return new InvestmentDecision(
                InvestmentAction.SELL,
                symbol,
                quantity,
                expectedPriceKrw,
                "Test SELL decision."
        );
    }
}
