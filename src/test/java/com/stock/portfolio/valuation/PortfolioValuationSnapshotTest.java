package com.stock.portfolio.valuation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PortfolioValuationSnapshotTest {
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T00:10:00Z");

    @Test
    void createsImmutableValuationSnapshot() {
        List<PortfolioPositionValuation> positions = new ArrayList<>();
        positions.add(position("005930", EVALUATED_AT.minusSeconds(30)));

        PortfolioValuationSnapshot snapshot =
                new PortfolioValuationSnapshot(
                        EVALUATED_AT,
                        300_000L,
                        800_000L,
                        1_100_000L,
                        positions
                );
        positions.clear();

        assertThat(snapshot.positions()).hasSize(1);
        assertThat(snapshot.positions())
                .isUnmodifiable();
        assertThat(snapshot.positionQuantity("005930")).isEqualTo(10L);
        assertThat(snapshot.positionEvaluationAmountKrw("005930"))
                .isEqualTo(800_000L);
        assertThat(snapshot.positionQuantity("000660")).isZero();
        assertThat(snapshot.positionEvaluationAmountKrw("000660"))
                .isZero();
    }

    @Test
    void rejectsPositionEvaluationAmountThatDoesNotMatchPositions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PortfolioValuationSnapshot(
                        EVALUATED_AT,
                        300_000L,
                        700_000L,
                        1_000_000L,
                        List.of(position(
                                "005930",
                                EVALUATED_AT.minusSeconds(30)
                        ))
                ))
                .withMessage(
                        "positionEvaluationAmountKrw must match positions."
                );
    }

    @Test
    void rejectsTotalAssetAmountThatDoesNotMatchComponents() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PortfolioValuationSnapshot(
                        EVALUATED_AT,
                        300_000L,
                        800_000L,
                        1_000_000L,
                        List.of(position(
                                "005930",
                                EVALUATED_AT.minusSeconds(30)
                        ))
                ))
                .withMessage(
                        "totalAssetAmountKrw must equal cash and position "
                                + "evaluation amount."
                );
    }

    @Test
    void rejectsPriceObservedAfterEvaluation() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PortfolioValuationSnapshot(
                        EVALUATED_AT,
                        300_000L,
                        800_000L,
                        1_100_000L,
                        List.of(position(
                                "005930",
                                EVALUATED_AT.plusSeconds(1)
                        ))
                ))
                .withMessage(
                        "priceObservedAt must not be after evaluatedAt. "
                                + "symbol=005930"
                );
    }

    @Test
    void rejectsInconsistentUnrealizedProfitLoss() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PortfolioPositionValuation(
                        "005930",
                        10L,
                        70_000L,
                        80_000L,
                        700_000L,
                        800_000L,
                        99_999L,
                        EVALUATED_AT.minusSeconds(30)
                ))
                .withMessage(
                        "unrealizedProfitLossKrw must match evaluation "
                                + "amount minus acquisition amount."
                );
    }

    private PortfolioPositionValuation position(
            String symbol,
            Instant priceObservedAt
    ) {
        return new PortfolioPositionValuation(
                symbol,
                10L,
                70_000L,
                80_000L,
                700_000L,
                800_000L,
                100_000L,
                priceObservedAt
        );
    }
}
