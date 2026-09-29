package com.stock.harness.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisResult;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
import com.stock.strategy.indicator.volatility.atr.AverageTrueRange;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessSwingV1DecisionEvidenceSnapshotTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 29);
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-29T06:00:00Z");
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:01:00Z");
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final HarnessRunSnapshotJsonConverter converter =
            new HarnessRunSnapshotJsonConverter(objectMapper);

    @Test
    void mapsSwingV1EvidenceFromInvestmentDecision() {
        InvestmentDecision decision = decision();

        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(
                decision
        );

        assertThat(snapshot.movingAverageEvidence()).isNull();
        assertThat(snapshot.swingV1Evidence()).isNotNull();
        assertThat(snapshot.swingV1Evidence().analysis().strategyIdentity())
                .isEqualTo(STRATEGY_IDENTITY);
        assertThat(snapshot.swingV1Evidence().currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(snapshot.swingV1Evidence()
                .portfolioValuation()
                .totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(snapshot.swingV1Evidence()
                .orderQuantityResult()
                .riskBudgetKrw()).isEqualTo(50_000L);
        assertThat(snapshot.swingV1Evidence()
                .orderQuantityResult()
                .finalQuantity()).isEqualTo(14L);
    }

    @Test
    void convertsSwingV1EvidenceSnapshotToJsonAndBack() {
        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(
                decision()
        );

        String json = converter.toDecisionJson(snapshot);
        HarnessDecisionSnapshot restored = converter.toDecisionSnapshot(
                json
        );

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.swingV1Evidence().analysis().symbol())
                .isEqualTo(SYMBOL);
        assertThat(restored.swingV1Evidence()
                .actionPolicyResult()
                .reasonCode()).isEqualTo(
                        SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY
                );
        assertThat(restored.swingV1Evidence()
                .orderQuantityResult()
                .reasonCode()).isEqualTo(
                        SwingV1OrderQuantityReasonCode
                                .HARNESS_CAPACITY_LIMITED_BUY
                );
    }

    @Test
    void serializesStrategyEvidenceWithoutGenericEvidenceProperty()
            throws Exception {
        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(decision())
        );

        assertThat(json.has("evidence")).isFalse();
        assertThat(json.get("movingAverageEvidence").isNull()).isTrue();
        assertThat(json.get("swingV1Evidence").isObject()).isTrue();
    }

    private InvestmentDecision decision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                SYMBOL,
                14L,
                70_000L,
                "Golden cross entry with ATR risk sizing.",
                evidence()
        );
    }

    private SwingV1DecisionEvidence evidence() {
        return new SwingV1DecisionEvidence(
                analysis(),
                new CurrentPriceSnapshot(
                        SYMBOL,
                        70_000L,
                        OBSERVED_AT
                ),
                CurrentPriceLookupSource.PROVIDER,
                new PortfolioValuationSnapshot(
                        EVALUATED_AT,
                        10_000_000L,
                        0L,
                        10_000_000L,
                        List.of()
                ),
                new SwingV1ActionPolicyResult(
                        InvestmentAction.BUY,
                        SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                        null,
                        "Golden cross entry signal detected."
                ),
                orderQuantityResult()
        );
    }

    private SwingV1OrderQuantityResult orderQuantityResult() {
        return new SwingV1OrderQuantityResult(
                InvestmentAction.BUY,
                SYMBOL,
                50_000L,
                new BigDecimal("2000.0"),
                25L,
                new OrderQuantityCapacity(
                        InvestmentAction.BUY,
                        SYMBOL,
                        70_000L,
                        0L,
                        142L,
                        14L,
                        42L,
                        14L,
                        new BigDecimal("0.007000")
                ),
                14L,
                SwingV1OrderQuantityReasonCode
                        .HARNESS_CAPACITY_LIMITED_BUY,
                "Harness capacity limited the BUY quantity."
        );
    }

    private SwingTechnicalAnalysisResult analysis() {
        MovingAverageAnalysisResult movingAverage =
                MovingAverageAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        61,
                        indicator(
                                AS_OF_DATE.minusDays(1),
                                "69000.00"
                        ),
                        MovingAverageTrend.DOWNTREND,
                        indicator(AS_OF_DATE, "71000.00"),
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS
                );
        AverageTrueRangeAnalysisResult averageTrueRange =
                AverageTrueRangeAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        61,
                        new AverageTrueRange(
                                SYMBOL,
                                14,
                                new BigDecimal("1000.00"),
                                AS_OF_DATE.minusDays(13),
                                AS_OF_DATE
                        )
                );
        return SwingTechnicalAnalysisResult.from(
                movingAverage,
                averageTrueRange
        );
    }

    private MovingAverageIndicator indicator(
            LocalDate date,
            String shortAveragePriceKrw
    ) {
        return new MovingAverageIndicator(
                SYMBOL,
                date,
                movingAverage(20, shortAveragePriceKrw, date),
                movingAverage(60, "70000.00", date)
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String averagePriceKrw,
            LocalDate date
    ) {
        return new SimpleMovingAverage(
                SYMBOL,
                period,
                new BigDecimal(averagePriceKrw),
                date.minusDays(period - 1L),
                date
        );
    }
}
