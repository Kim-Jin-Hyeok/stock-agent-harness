package com.stock.agent.decision.swing.v1.policy;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisStatus;
import com.stock.strategy.indicator.volatility.atr.AverageTrueRange;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Component
public class SwingV1ActionPolicy {
    private static final String STRATEGY_ID = "SWING_V1";
    private static final int STRATEGY_VERSION = 1;
    private static final BigDecimal INITIAL_STOP_ATR_MULTIPLIER =
            new BigDecimal("2.0");

    public SwingV1ActionPolicyResult decide(
            SwingTechnicalAnalysisResult analysis,
            PortfolioSnapshot portfolioSnapshot,
            CurrentPriceSnapshot currentPriceSnapshot
    ) {
        Objects.requireNonNull(analysis, "analysis must not be null.");
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        Objects.requireNonNull(
                currentPriceSnapshot,
                "currentPriceSnapshot must not be null."
        );

        validateStrategyIdentity(analysis.strategyIdentity());
        validateCurrentPrice(analysis.symbol(), currentPriceSnapshot);

        if (analysis.status()
                == SwingTechnicalAnalysisStatus.INSUFFICIENT_DATA) {
            return result(
                    InvestmentAction.HOLD,
                    SwingV1ActionReasonCode.INSUFFICIENT_DAILY_PRICE_HISTORY,
                    null,
                    "Swing technical data is insufficient. symbol="
                            + analysis.symbol()
                            + ", requiredBars="
                            + analysis.requiredBarCount()
                            + ", availableBars="
                            + analysis.availableBarCount()
            );
        }

        PositionSummary position = summarizePosition(
                portfolioSnapshot,
                analysis.symbol()
        );
        MovingAverageCrossoverSignal crossoverSignal =
                analysis.movingAverageAnalysis().crossoverSignal();

        if (!position.hasPosition()) {
            return decideWithoutPosition(
                    analysis.symbol(),
                    crossoverSignal
            );
        }

        return decideWithPosition(
                analysis,
                position,
                currentPriceSnapshot,
                crossoverSignal
        );
    }

    private SwingV1ActionPolicyResult decideWithoutPosition(
            String symbol,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        if (crossoverSignal == MovingAverageCrossoverSignal.GOLDEN_CROSS) {
            return result(
                    InvestmentAction.BUY,
                    SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                    null,
                    "Golden cross entry signal detected. symbol=" + symbol
            );
        }

        return result(
                InvestmentAction.HOLD,
                SwingV1ActionReasonCode.NO_ENTRY_SIGNAL,
                null,
                "No swing entry signal detected. symbol=" + symbol
        );
    }

    private SwingV1ActionPolicyResult decideWithPosition(
            SwingTechnicalAnalysisResult analysis,
            PositionSummary position,
            CurrentPriceSnapshot currentPriceSnapshot,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        BigDecimal atrStopPriceKrw = atrStopPriceKrw(
                position.averagePriceKrw(),
                analysis.averageTrueRangeAnalysis().averageTrueRange()
        );
        BigDecimal currentPriceKrw = BigDecimal.valueOf(
                currentPriceSnapshot.priceKrw()
        );

        if (currentPriceKrw.compareTo(atrStopPriceKrw) <= 0) {
            return result(
                    InvestmentAction.SELL,
                    SwingV1ActionReasonCode.ATR_INITIAL_STOP,
                    atrStopPriceKrw,
                    "Current price reached the initial ATR stop. symbol="
                            + analysis.symbol()
                            + ", currentPriceKrw="
                            + currentPriceKrw
                            + ", atrStopPriceKrw="
                            + atrStopPriceKrw
            );
        }

        if (crossoverSignal == MovingAverageCrossoverSignal.DEAD_CROSS) {
            return result(
                    InvestmentAction.SELL,
                    SwingV1ActionReasonCode.DEAD_CROSS_EXIT,
                    atrStopPriceKrw,
                    "Dead cross exit signal detected. symbol="
                            + analysis.symbol()
            );
        }

        if (crossoverSignal == MovingAverageCrossoverSignal.GOLDEN_CROSS) {
            return result(
                    InvestmentAction.HOLD,
                    SwingV1ActionReasonCode.POSITION_ALREADY_HELD,
                    atrStopPriceKrw,
                    "Additional entry is not allowed for SWING_V1. symbol="
                            + analysis.symbol()
            );
        }

        return result(
                InvestmentAction.HOLD,
                SwingV1ActionReasonCode.HOLD_POSITION,
                atrStopPriceKrw,
                "No swing exit signal detected. symbol="
                        + analysis.symbol()
        );
    }

    private BigDecimal atrStopPriceKrw(
            BigDecimal averagePriceKrw,
            AverageTrueRange averageTrueRange
    ) {
        BigDecimal stopDistanceKrw = averageTrueRange
                .averageTrueRangeKrw()
                .multiply(INITIAL_STOP_ATR_MULTIPLIER);
        return averagePriceKrw
                .subtract(stopDistanceKrw)
                .max(BigDecimal.ZERO);
    }

    private PositionSummary summarizePosition(
            PortfolioSnapshot portfolioSnapshot,
            String symbol
    ) {
        List<PortfolioPosition> positions = Objects.requireNonNull(
                portfolioSnapshot.positions(),
                "portfolioSnapshot.positions must not be null."
        );
        long totalQuantity = 0L;
        BigDecimal totalAcquisitionAmountKrw = BigDecimal.ZERO;

        for (PortfolioPosition position : positions) {
            Objects.requireNonNull(position, "position must not be null.");
            if (!symbol.equals(position.symbol())) {
                continue;
            }
            if (position.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "Position quantity must be positive. symbol=" + symbol
                );
            }
            if (position.averagePriceKrw() <= 0) {
                throw new IllegalArgumentException(
                        "Position average price must be positive. symbol="
                                + symbol
                );
            }

            totalQuantity = Math.addExact(
                    totalQuantity,
                    position.quantity()
            );
            totalAcquisitionAmountKrw = totalAcquisitionAmountKrw.add(
                    BigDecimal.valueOf(position.averagePriceKrw())
                            .multiply(BigDecimal.valueOf(position.quantity()))
            );
        }

        if (totalQuantity == 0L) {
            return PositionSummary.empty();
        }

        BigDecimal averagePriceKrw = totalAcquisitionAmountKrw.divide(
                BigDecimal.valueOf(totalQuantity),
                8,
                RoundingMode.HALF_UP
        );
        return new PositionSummary(totalQuantity, averagePriceKrw);
    }

    private void validateStrategyIdentity(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        if (!STRATEGY_ID.equals(strategyIdentity.strategyId())
                || strategyIdentity.strategyVersion() != STRATEGY_VERSION
                || strategyIdentity.horizon() != InvestmentHorizon.SWING) {
            throw new IllegalArgumentException(
                    "analysis must use SWING_V1 strategy identity."
            );
        }
    }

    private void validateCurrentPrice(
            String symbol,
            CurrentPriceSnapshot currentPriceSnapshot
    ) {
        if (!symbol.equals(currentPriceSnapshot.symbol())) {
            throw new IllegalArgumentException(
                    "Current price symbol must match analysis symbol."
            );
        }
        if (currentPriceSnapshot.priceKrw() <= 0) {
            throw new IllegalArgumentException(
                    "Current price must be positive."
            );
        }
        Objects.requireNonNull(
                currentPriceSnapshot.observedAt(),
                "currentPriceSnapshot.observedAt must not be null."
        );
    }

    private SwingV1ActionPolicyResult result(
            InvestmentAction action,
            SwingV1ActionReasonCode reasonCode,
            BigDecimal atrStopPriceKrw,
            String reason
    ) {
        return new SwingV1ActionPolicyResult(
                action,
                reasonCode,
                atrStopPriceKrw,
                reason
        );
    }

    private record PositionSummary(
            long quantity,
            BigDecimal averagePriceKrw
    ) {
        private static PositionSummary empty() {
            return new PositionSummary(0L, null);
        }

        private boolean hasPosition() {
            return quantity > 0L;
        }
    }
}
