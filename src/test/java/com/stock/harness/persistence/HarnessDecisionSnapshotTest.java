package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessDecisionSnapshotTest {
    @Test
    void getBuyDecisionSnapshot() {
        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(buyDecision());

        assertThat(snapshot.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.quantity()).isEqualTo(10L);
        assertThat(snapshot.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(snapshot.estimatedOrderAmountKrw()).isEqualTo(snapshot.quantity() * snapshot.expectedPriceKrw());
        assertThat(snapshot.reason()).isEqualTo("Buy Samsung Electronics.");
    }

    @Test
    void getSellDecisionSnapshot() {
        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(sellDecision());

        assertThat(snapshot.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.quantity()).isEqualTo(5L);
        assertThat(snapshot.expectedPriceKrw()).isEqualTo(75_000L);
        assertThat(snapshot.estimatedOrderAmountKrw()).isEqualTo(snapshot.quantity() * snapshot.expectedPriceKrw());
        assertThat(snapshot.reason()).isEqualTo("Sell Samsung Electronics.");
    }

    @Test
    void getHoldDecisionSnapshot() {
        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(holdDecision());

        assertThat(snapshot.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(snapshot.symbol()).isNull();
        assertThat(snapshot.quantity()).isNull();
        assertThat(snapshot.expectedPriceKrw()).isNull();
        assertThat(snapshot.estimatedOrderAmountKrw()).isZero();
        assertThat(snapshot.reason()).isEqualTo("No trade decision.");
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                "Buy Samsung Electronics."
        );
    }

    private InvestmentDecision sellDecision() {
        return new InvestmentDecision(
                InvestmentAction.SELL,
                "005930",
                5L,
                75_000L,
                "Sell Samsung Electronics."
        );
    }

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "No trade decision."
        );
    }
}
