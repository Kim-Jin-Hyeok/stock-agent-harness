package com.stock.agent.decision.movingaverage.resolution;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.movingaverage.MovingAverageActionPolicy;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidationReasonCode;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidationResult;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidationStatus;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidator;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskProperties;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageOrderDecisionResolverTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final MovingAverageOrderDecisionResolver resolver =
            new MovingAverageOrderDecisionResolver(
                    new MovingAverageOrderProposalValidator()
            );
    private final MovingAverageOrderDecisionContextFactory contextFactory =
            new MovingAverageOrderDecisionContextFactory(
                    new MovingAverageActionPolicy(),
                    new OrderQuantityCapacityCalculator(
                            new RiskProperties(0.1, 0.3)
                    )
            );

    @Test
    void resolvesValidBuyProposalUsingTrustedContextValues() {
        MovingAverageOrderDecisionContext context = buyContext(
                100_000L,
                10_000_000L
        );

        MovingAverageOrderDecisionResolution resolution = resolver.resolve(
                context,
                OrderQuantityProposal.execute(
                        5L,
                        "Use half of the allowed capacity."
                )
        );

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.validationResult().status())
                .isEqualTo(MovingAverageOrderProposalValidationStatus.VALID);
        assertThat(resolution.decision().action())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(resolution.decision().symbol()).isEqualTo(SYMBOL);
        assertThat(resolution.decision().quantity()).isEqualTo(5L);
        assertThat(resolution.decision().expectedPriceKrw())
                .isEqualTo(100_000L);
        assertThat(resolution.decision().reason())
                .isEqualTo("Use half of the allowed capacity.");
        assertThat(resolution.decision().movingAverageEvidence().analysis())
                .isEqualTo(context.analysisResult());
        assertThat(
                resolution.decision()
                        .movingAverageEvidence()
                        .currentPriceSource()
        ).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(
                resolution.decision()
                        .movingAverageEvidence()
                        .orderDecisionEvidence()
                        .quantityCapacity()
        ).isEqualTo(context.quantityCapacity());
        assertThat(
                resolution.decision()
                        .movingAverageEvidence()
                        .orderDecisionEvidence()
                        .proposal()
        ).isEqualTo(OrderQuantityProposal.execute(
                5L,
                "Use half of the allowed capacity."
        ));
    }

    @Test
    void resolvesValidSellProposalUsingSignalAction() {
        MovingAverageOrderDecisionResolution resolution = resolver.resolve(
                sellContext(5L),
                OrderQuantityProposal.execute(
                        3L,
                        "Reduce the current position."
                )
        );

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.decision().action())
                .isEqualTo(InvestmentAction.SELL);
        assertThat(resolution.decision().quantity()).isEqualTo(3L);
        assertThat(resolution.decision().expectedPriceKrw())
                .isEqualTo(100_000L);
    }

    @Test
    void resolvesHoldProposalWithoutOrderFields() {
        MovingAverageOrderDecisionResolution resolution = resolver.resolve(
                buyContext(100_000L, 10_000_000L),
                OrderQuantityProposal.hold(
                        "Additional confirmation is required."
                )
        );

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.decision().action())
                .isEqualTo(InvestmentAction.HOLD);
        assertThat(resolution.decision().symbol()).isNull();
        assertThat(resolution.decision().quantity()).isNull();
        assertThat(resolution.decision().expectedPriceKrw()).isNull();
        assertThat(resolution.decision().movingAverageEvidence()).isNotNull();
        assertThat(
                resolution.decision()
                        .movingAverageEvidence()
                        .orderDecisionEvidence()
                        .proposal()
                        .intent()
        ).isEqualTo(OrderDecisionIntent.HOLD);
    }

    @Test
    void rejectsProposalExceedingAllowedCapacity() {
        MovingAverageOrderDecisionResolution resolution = resolver.resolve(
                buyContext(100_000L, 10_000_000L),
                OrderQuantityProposal.execute(
                        11L,
                        "Attempt more than the allowed capacity."
                )
        );

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.decision()).isNull();
        assertThat(resolution.validationResult().reasonCode())
                .isEqualTo(
                        MovingAverageOrderProposalValidationReasonCode
                                .QUANTITY_EXCEEDS_ALLOWED_CAPACITY
                );
    }

    @Test
    void rejectsProposalWhenOrderCapacityIsUnavailable() {
        MovingAverageOrderDecisionResolution resolution = resolver.resolve(
                buyContext(400_000L, 3_333_334L),
                OrderQuantityProposal.execute(
                        1L,
                        "Attempt one share."
                )
        );

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.decision()).isNull();
        assertThat(resolution.validationResult().reasonCode())
                .isEqualTo(
                        MovingAverageOrderProposalValidationReasonCode
                                .ORDER_CAPACITY_UNAVAILABLE
                );
    }

    @Test
    void validResolutionRequiresDecision() {
        MovingAverageOrderProposalValidationResult validationResult =
                MovingAverageOrderProposalValidationResult.valid(
                        OrderDecisionIntent.HOLD
                );

        assertThatThrownBy(() -> new MovingAverageOrderDecisionResolution(
                validationResult,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valid resolution requires a decision.");
    }

    @Test
    void invalidResolutionMustNotContainDecision() {
        MovingAverageOrderProposalValidationResult validationResult =
                MovingAverageOrderProposalValidationResult.invalid(
                        OrderDecisionIntent.EXECUTE_ORDER,
                        MovingAverageOrderProposalValidationReasonCode
                                .ORDER_CAPACITY_UNAVAILABLE,
                        "Order capacity is unavailable."
                );
        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "No order."
        );

        assertThatThrownBy(() -> new MovingAverageOrderDecisionResolution(
                validationResult,
                decision
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Invalid resolution must not contain a decision."
                );
    }

    private MovingAverageOrderDecisionContext buyContext(
            long currentPriceKrw,
            long totalAssetAmountKrw
    ) {
        return context(
                MovingAverageCrossoverSignal.GOLDEN_CROSS,
                emptyPortfolio(totalAssetAmountKrw),
                currentPriceKrw
        );
    }

    private MovingAverageOrderDecisionContext sellContext(long quantity) {
        return context(
                MovingAverageCrossoverSignal.DEAD_CROSS,
                new PortfolioSnapshot(
                        9_500_000L,
                        10_000_000L,
                        List.of(new PortfolioPosition(
                                SYMBOL,
                                quantity,
                                100_000L,
                                quantity * 100_000L
                        ))
                ),
                100_000L
        );
    }

    private MovingAverageOrderDecisionContext context(
            MovingAverageCrossoverSignal signal,
            PortfolioSnapshot portfolioSnapshot,
            long currentPriceKrw
    ) {
        return contextFactory.create(
                STRATEGY_IDENTITY,
                portfolioSnapshot,
                analyzedResult(signal),
                CurrentPriceLookupResult.provider(
                        new CurrentPriceSnapshot(
                                SYMBOL,
                                currentPriceKrw,
                                Instant.parse("2026-09-26T09:00:00Z")
                        )
                )
        );
    }

    private PortfolioSnapshot emptyPortfolio(long totalAssetAmountKrw) {
        return new PortfolioSnapshot(
                totalAssetAmountKrw,
                totalAssetAmountKrw,
                List.of()
        );
    }

    private MovingAverageAnalysisResult analyzedResult(
            MovingAverageCrossoverSignal signal
    ) {
        LocalDate currentDate = LocalDate.of(2026, 9, 26);
        return MovingAverageAnalysisResult.analyzed(
                STRATEGY_IDENTITY,
                60,
                indicator(currentDate.minusDays(1), "70000.00"),
                MovingAverageTrend.FLAT,
                indicator(currentDate, currentShortAverage(signal)),
                currentTrend(signal),
                signal
        );
    }

    private String currentShortAverage(
            MovingAverageCrossoverSignal signal
    ) {
        return switch (signal) {
            case GOLDEN_CROSS -> "71000.00";
            case DEAD_CROSS -> "69000.00";
            case NONE -> "70000.00";
        };
    }

    private MovingAverageTrend currentTrend(
            MovingAverageCrossoverSignal signal
    ) {
        return switch (signal) {
            case GOLDEN_CROSS -> MovingAverageTrend.UPTREND;
            case DEAD_CROSS -> MovingAverageTrend.DOWNTREND;
            case NONE -> MovingAverageTrend.FLAT;
        };
    }

    private MovingAverageIndicator indicator(
            LocalDate asOfDate,
            String shortAveragePriceKrw
    ) {
        return new MovingAverageIndicator(
                SYMBOL,
                asOfDate,
                movingAverage(5, shortAveragePriceKrw, asOfDate),
                movingAverage(20, "70000.00", asOfDate)
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String averagePriceKrw,
            LocalDate asOfDate
    ) {
        return new SimpleMovingAverage(
                SYMBOL,
                period,
                new BigDecimal(averagePriceKrw),
                asOfDate.minusDays(period - 1L),
                asOfDate
        );
    }
}
