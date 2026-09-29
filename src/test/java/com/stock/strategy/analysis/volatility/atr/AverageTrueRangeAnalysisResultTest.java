package com.stock.strategy.analysis.volatility.atr;

import com.stock.strategy.indicator.volatility.atr.AverageTrueRange;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AverageTrueRangeAnalysisResultTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    @Test
    void createsAnalyzedResult() {
        AverageTrueRange averageTrueRange = averageTrueRange();

        AverageTrueRangeAnalysisResult result =
                AverageTrueRangeAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        120,
                        averageTrueRange
                );

        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.status())
                .isEqualTo(AverageTrueRangeAnalysisStatus.ANALYZED);
        assertThat(result.requiredBarCount()).isEqualTo(15);
        assertThat(result.availableBarCount()).isEqualTo(120);
        assertThat(result.averageTrueRange()).isEqualTo(averageTrueRange);
    }

    @Test
    void createsInsufficientDataResultWithoutAnalysis() {
        AverageTrueRangeAnalysisResult result =
                AverageTrueRangeAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        15,
                        14
                );

        assertThat(result.status())
                .isEqualTo(AverageTrueRangeAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(result.requiredBarCount()).isEqualTo(15);
        assertThat(result.availableBarCount()).isEqualTo(14);
        assertThat(result.averageTrueRange()).isNull();
    }

    @Test
    void rejectsAnalyzedResultWithMismatchedSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AverageTrueRangeAnalysisResult(
                        STRATEGY_IDENTITY,
                        "000660",
                        AverageTrueRangeAnalysisStatus.ANALYZED,
                        15,
                        120,
                        averageTrueRange()
                ))
                .withMessage(
                        "symbol must match average true range symbol."
                );
    }

    @Test
    void rejectsAnalyzedResultWithoutEnoughBars() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AverageTrueRangeAnalysisResult(
                        STRATEGY_IDENTITY,
                        "005930",
                        AverageTrueRangeAnalysisStatus.ANALYZED,
                        15,
                        14,
                        averageTrueRange()
                ))
                .withMessage(
                        "Analyzed result requires enough available bars."
                );
    }

    @Test
    void rejectsAnalyzedResultWithMismatchedRequiredBarCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AverageTrueRangeAnalysisResult(
                        STRATEGY_IDENTITY,
                        "005930",
                        AverageTrueRangeAnalysisStatus.ANALYZED,
                        16,
                        120,
                        averageTrueRange()
                ))
                .withMessage(
                        "requiredBarCount must be one greater than average "
                                + "true range period."
                );
    }

    @Test
    void rejectsInsufficientDataResultContainingAnalysis() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AverageTrueRangeAnalysisResult(
                        STRATEGY_IDENTITY,
                        "005930",
                        AverageTrueRangeAnalysisStatus.INSUFFICIENT_DATA,
                        15,
                        14,
                        averageTrueRange()
                ))
                .withMessage(
                        "Insufficient data result must not contain analysis."
                );
    }

    private AverageTrueRange averageTrueRange() {
        return new AverageTrueRange(
                "005930",
                14,
                new BigDecimal("1250.50"),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 24)
        );
    }
}
