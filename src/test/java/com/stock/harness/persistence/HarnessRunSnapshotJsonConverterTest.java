package com.stock.harness.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.InvestmentAction;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessRunSnapshotJsonConverterTest {
    private final HarnessRunSnapshotJsonConverter converter = new HarnessRunSnapshotJsonConverter(
            new ObjectMapper()
    );

    @Test
    void convertsDecisionSnapshotToJsonAndBack() {
        HarnessDecisionSnapshot snapshot = decisionSnapshot();

        String json = converter.toDecisionJson(snapshot);
        HarnessDecisionSnapshot restored = converter.toDecisionSnapshot(json);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(restored.symbol()).isEqualTo("005930");
        assertThat(restored.quantity()).isEqualTo(10L);
        assertThat(restored.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(restored.estimatedOrderAmountKrw()).isEqualTo(700_000L);
        assertThat(restored.reason()).isEqualTo("Buy Samsung Electronics.");
    }

    @Test
    void convertsRiskCheckSnapshotToJsonAndBack() {
        HarnessRiskCheckSnapshot snapshot = riskCheckSnapshot();

        String json = converter.toRiskCheckJson(snapshot);
        HarnessRiskCheckSnapshot restored = converter.toRiskCheckSnapshot(json);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(restored.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(restored.symbol()).isEqualTo("005930");
        assertThat(restored.quantity()).isEqualTo(10L);
        assertThat(restored.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(restored.estimatedOrderAmountKrw()).isEqualTo(700_000L);
        assertThat(restored.reasonCode()).isEqualTo(RiskReasonCode.RISK_APPROVED);
        assertThat(restored.reason()).isEqualTo("Risk check approved.");
    }

    private HarnessDecisionSnapshot decisionSnapshot() {
        return new HarnessDecisionSnapshot(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                "Buy Samsung Electronics."
        );
    }

    private HarnessRiskCheckSnapshot riskCheckSnapshot() {
        return new HarnessRiskCheckSnapshot(
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
}
