package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessRiskCheckSnapshotTest {
    @Test
    void getApprovedSnapshot() {
        HarnessRiskCheckSnapshot snapshot = HarnessRiskCheckSnapshot.from(approvedBuyRiskCheckResult());

        assertThat(snapshot.status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(snapshot.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.quantity()).isEqualTo(10L);
        assertThat(snapshot.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(snapshot.estimatedOrderAmountKrw()).isEqualTo(700_000L);
        assertThat(snapshot.reasonCode()).isEqualTo(RiskReasonCode.RISK_APPROVED);
        assertThat(snapshot.reason()).isEqualTo("Risk check approved.");
    }

    @Test
    void getDeniedSnapshot() {
        HarnessRiskCheckSnapshot snapshot = HarnessRiskCheckSnapshot.from(deniedBuyRiskCheckResult());

        assertThat(snapshot.status()).isEqualTo(RiskCheckStatus.DENIED);
        assertThat(snapshot.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.quantity()).isEqualTo(100L);
        assertThat(snapshot.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(snapshot.estimatedOrderAmountKrw()).isEqualTo(7_000_000L);
        assertThat(snapshot.reasonCode()).isEqualTo(RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED);
        assertThat(snapshot.reason()).isEqualTo("Max order ratio exceeded.");
    }

    @Test
    void getHoldSnapshot() {
        HarnessRiskCheckSnapshot snapshot = HarnessRiskCheckSnapshot.from(approvedHoldRiskCheckResult());

        assertThat(snapshot.status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(snapshot.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(snapshot.symbol()).isNull();
        assertThat(snapshot.quantity()).isNull();
        assertThat(snapshot.expectedPriceKrw()).isNull();
        assertThat(snapshot.reasonCode()).isEqualTo(RiskReasonCode.RISK_APPROVED);
        assertThat(snapshot.estimatedOrderAmountKrw()).isZero();
        assertThat(snapshot.reason()).isEqualTo("HOLD decision approved.");
    }

    private RiskCheckResult approvedBuyRiskCheckResult() {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                RiskReasonCode.RISK_APPROVED,
                "Risk check approved."
        );
    }

    private RiskCheckResult deniedBuyRiskCheckResult() {
        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                InvestmentAction.BUY,
                "005930",
                100L,
                70_000L,
                7_000_000L,
                RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED,
                "Max order ratio exceeded."
        );
    }

    private RiskCheckResult approvedHoldRiskCheckResult() {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                InvestmentAction.HOLD,
                null,
                null,
                null,
                0L,
                RiskReasonCode.RISK_APPROVED,
                "HOLD decision approved."
        );
    }
}
