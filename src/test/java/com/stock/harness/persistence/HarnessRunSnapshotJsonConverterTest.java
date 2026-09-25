package com.stock.harness.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.InvestmentAction;
import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessRunSnapshotJsonConverterTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private final HarnessRunSnapshotJsonConverter converter = new HarnessRunSnapshotJsonConverter(
            new ObjectMapper().findAndRegisterModules()
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
        assertThat(restored.movingAverageEvidence())
                .isEqualTo(movingAverageEvidenceSnapshot());
    }

    @Test
    void restoresLegacyDecisionSnapshotWithoutMovingAverageEvidence() {
        String json = """
                {
                  "action": "HOLD",
                  "symbol": null,
                  "quantity": null,
                  "expectedPriceKrw": null,
                  "estimatedOrderAmountKrw": 0,
                  "reason": "No trade decision."
                }
                """;

        HarnessDecisionSnapshot restored = converter.toDecisionSnapshot(json);

        assertThat(restored.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(restored.reason()).isEqualTo("No trade decision.");
        assertThat(restored.movingAverageEvidence()).isNull();
    }

    @Test
    void restoresLegacyMovingAverageEvidenceWithoutCrossoverFields() {
        String json = """
                {
                  "action": "HOLD",
                  "symbol": null,
                  "quantity": null,
                  "expectedPriceKrw": null,
                  "estimatedOrderAmountKrw": 0,
                  "reason": "Moving average analyzed.",
                  "movingAverageEvidence": {
                    "status": "ANALYZED",
                    "symbol": "005930",
                    "requiredBarCount": 20,
                    "availableBarCount": 60,
                    "trend": "UPTREND",
                    "shortPeriod": 5,
                    "shortAveragePriceKrw": 71000.00,
                    "longPeriod": 20,
                    "longAveragePriceKrw": 70000.00,
                    "asOfTradingDate": "2026-01-02",
                    "currentPriceKrw": 72000,
                    "currentPriceSource": "PROVIDER"
                  }
                }
                """;

        HarnessDecisionSnapshot restored = converter.toDecisionSnapshot(json);

        HarnessMovingAverageEvidenceSnapshot evidence =
                restored.movingAverageEvidence();
        assertThat(evidence.previousTrend()).isNull();
        assertThat(evidence.previousShortAveragePriceKrw()).isNull();
        assertThat(evidence.previousLongAveragePriceKrw()).isNull();
        assertThat(evidence.previousAsOfTradingDate()).isNull();
        assertThat(evidence.crossoverSignal()).isNull();
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

    @Test
    void convertsPortfolioSnapshotToJsonAndBack() {
        HarnessPortfolioSnapshot snapshot = portfolioSnapshot();

        String json = converter.toPortfolioJson(snapshot);
        HarnessPortfolioSnapshot restored = converter.toPortfolioSnapshot(json);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.cashAmountKrw()).isEqualTo(8_700_000L);
        assertThat(restored.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(restored.positions()).hasSize(2);

        HarnessPortfolioPositionSnapshot firstPosition = restored.positions().getFirst();
        assertThat(firstPosition.symbol()).isEqualTo("005930");
        assertThat(firstPosition.quantity()).isEqualTo(10L);
        assertThat(firstPosition.averagePriceKrw()).isEqualTo(70_000L);
        assertThat(firstPosition.marketValueKrw()).isEqualTo(700_000L);

        HarnessPortfolioPositionSnapshot secondPosition = restored.positions().getLast();
        assertThat(secondPosition.symbol()).isEqualTo("000660");
        assertThat(secondPosition.quantity()).isEqualTo(5L);
        assertThat(secondPosition.averagePriceKrw()).isEqualTo(120_000L);
        assertThat(secondPosition.marketValueKrw()).isEqualTo(600_000L);
    }

    @Test
    void convertsMarketSnapshotToJsonAndBack() {
        HarnessMarketSnapshot snapshot = marketSnapshot();

        String json = converter.toMarketJson(snapshot);
        HarnessMarketSnapshot restored = converter.toMarketSnapshot(json);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.market()).isEqualTo("KR");
        assertThat(restored.marketOpen()).isTrue();
        assertThat(restored.description()).isEqualTo("Korean market is open.");
    }

    @Test
    void convertsToolExecutionSnapshotsToJsonAndBack() {
        List<HarnessToolExecutionSnapshot> snapshots = List.of(
                new HarnessToolExecutionSnapshot(
                        HarnessToolExecutionStatus.EXECUTED,
                        HarnessToolType.GET_PORTFOLIO,
                        HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                        "Harness tool execution completed.",
                        portfolioSnapshot(),
                        null
                ),
                new HarnessToolExecutionSnapshot(
                        HarnessToolExecutionStatus.EXECUTED,
                        HarnessToolType.GET_MARKET,
                        HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                        "Harness tool execution completed.",
                        null,
                        marketSnapshot()
                ),
                new HarnessToolExecutionSnapshot(
                        HarnessToolExecutionStatus.EXECUTED,
                        HarnessToolType.GET_DAILY_PRICE_HISTORY,
                        HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                        "Harness tool execution completed.",
                        null,
                        null,
                        null,
                        new HarnessToolRequestSnapshot(
                                HarnessToolType.GET_DAILY_PRICE_HISTORY,
                                "005930"
                        ),
                        null,
                        dailyPriceHistorySnapshot()
                ),
                new HarnessToolExecutionSnapshot(
                        HarnessToolExecutionStatus.EXECUTED,
                        HarnessToolType.GET_CURRENT_PRICE,
                        HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                        "Harness tool execution completed.",
                        null,
                        null,
                        new HarnessCurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT),
                        new HarnessToolRequestSnapshot(
                                HarnessToolType.GET_CURRENT_PRICE,
                                "005930"
                        ),
                        CurrentPriceLookupSource.PROVIDER
                )
        );

        String json = converter.toToolExecutionsJson(snapshots);
        List<HarnessToolExecutionSnapshot> restored = converter.toToolExecutionSnapshots(json);

        assertThat(restored).isEqualTo(snapshots);
        assertThat(restored)
                .extracting(HarnessToolExecutionSnapshot::type)
                .containsExactly(
                        HarnessToolType.GET_PORTFOLIO,
                        HarnessToolType.GET_MARKET,
                        HarnessToolType.GET_DAILY_PRICE_HISTORY,
                        HarnessToolType.GET_CURRENT_PRICE
                );
        assertThat(restored.get(2).dailyPriceHistorySnapshot())
                .isEqualTo(dailyPriceHistorySnapshot());
        assertThat(restored.getLast().request()).isEqualTo(
                new HarnessToolRequestSnapshot(HarnessToolType.GET_CURRENT_PRICE, "005930")
        );
        assertThat(restored.getLast().currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(restored.getLast().currentPriceSnapshot().observedAt())
                .isEqualTo(OBSERVED_AT);
    }

    @Test
    void restoresLegacyToolExecutionSnapshotWithoutCurrentPriceMetadata() {
        String json = """
                [{
                  "status": "EXECUTED",
                  "type": "GET_CURRENT_PRICE",
                  "reasonCode": "TOOL_EXECUTED",
                  "reason": "Harness tool execution completed.",
                  "currentPriceSnapshot": {
                    "symbol": "005930",
                    "priceKrw": 70000
                  },
                  "request": {
                    "type": "GET_CURRENT_PRICE",
                    "symbol": "005930"
                  }
                }]
                """;

        List<HarnessToolExecutionSnapshot> restored = converter.toToolExecutionSnapshots(json);

        assertThat(restored).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.currentPriceSnapshot())
                    .isEqualTo(new HarnessCurrentPriceSnapshot("005930", 70_000L, null));
            assertThat(snapshot.currentPriceSnapshot().observedAt()).isNull();
            assertThat(snapshot.currentPriceSource()).isNull();
        });
    }

    @Test
    void convertsCandidateSymbolsToJsonAndBack() {
        List<String> candidateSymbols = List.of("005930", "000660");

        String json = converter.toCandidateSymbolsJson(candidateSymbols);
        List<String> restored = converter.toCandidateSymbols(json);

        assertThat(restored).containsExactly("005930", "000660");
        assertThatThrownBy(() -> restored.add("035420"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private HarnessDecisionSnapshot decisionSnapshot() {
        return new HarnessDecisionSnapshot(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                "Buy Samsung Electronics.",
                movingAverageEvidenceSnapshot()
        );
    }

    private HarnessMovingAverageEvidenceSnapshot movingAverageEvidenceSnapshot() {
        return new HarnessMovingAverageEvidenceSnapshot(
                MovingAverageAnalysisStatus.ANALYZED,
                "005930",
                21,
                60,
                MovingAverageTrend.UPTREND,
                5,
                new BigDecimal("71000.00"),
                20,
                new BigDecimal("70000.00"),
                LocalDate.of(2026, 1, 2),
                72_000L,
                CurrentPriceLookupSource.PROVIDER,
                MovingAverageTrend.FLAT,
                new BigDecimal("70000.00"),
                new BigDecimal("70000.00"),
                LocalDate.of(2026, 1, 1),
                MovingAverageCrossoverSignal.GOLDEN_CROSS
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

    private HarnessPortfolioSnapshot portfolioSnapshot() {
        return new HarnessPortfolioSnapshot(
                8_700_000L,
                10_000_000L,
                List.of(
                        samsungPositionSnapshot(),
                        skHynixPositionSnapshot()
                )
        );
    }

    private HarnessPortfolioPositionSnapshot samsungPositionSnapshot() {
        return new HarnessPortfolioPositionSnapshot(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }

    private HarnessPortfolioPositionSnapshot skHynixPositionSnapshot() {
        return new HarnessPortfolioPositionSnapshot(
                "000660",
                5L,
                120_000L,
                600_000L
        );
    }

    private HarnessMarketSnapshot marketSnapshot() {
        return new HarnessMarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }

    private HarnessDailyPriceHistorySnapshot dailyPriceHistorySnapshot() {
        return new HarnessDailyPriceHistorySnapshot(
                "005930",
                List.of(new HarnessDailyPriceBarSnapshot(
                        LocalDate.of(2026, 1, 2),
                        69_000L,
                        71_000L,
                        68_000L,
                        70_000L,
                        1_000_000L
                ))
        );
    }
}
