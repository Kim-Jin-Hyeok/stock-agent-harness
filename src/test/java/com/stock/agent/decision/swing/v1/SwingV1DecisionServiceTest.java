package com.stock.agent.decision.swing.v1;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.policy.SwingV1ActionPolicy;
import com.stock.agent.decision.swing.v1.quantity.policy.SwingV1OrderQuantityPolicy;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.resolution.SwingV1DecisionResolver;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.valuation.PortfolioPositionValuation;
import com.stock.portfolio.valuation.PortfolioValuationService;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwingV1DecisionServiceTest {
    private static final String CANDIDATE_SYMBOL = "005930";
    private static final String POSITION_SYMBOL = "000660";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate AS_OF_DATE =
            LocalDate.of(2026, 9, 29);
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-29T06:00:00Z");
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:01:00Z");

    private final SwingTechnicalAnalysisService analysisService =
            mock(SwingTechnicalAnalysisService.class);
    private final SwingV1ActionPolicy actionPolicy =
            mock(SwingV1ActionPolicy.class);
    private final PortfolioValuationService portfolioValuationService =
            mock(PortfolioValuationService.class);
    private final SwingV1OrderQuantityPolicy orderQuantityPolicy =
            mock(SwingV1OrderQuantityPolicy.class);
    private final SwingV1DecisionService decisionService =
            new SwingV1DecisionService(
                    analysisService,
                    actionPolicy,
                    portfolioValuationService,
                    orderQuantityPolicy,
                    new SwingV1DecisionResolver()
            );

    @Test
    void decideRunsSharedSwingV1PolicyPipeline() {
        DailyPriceHistory history = new DailyPriceHistory(
                CANDIDATE_SYMBOL,
                List.of()
        );
        PortfolioSnapshot portfolio = portfolio();
        CurrentPriceSnapshot candidatePrice = currentPrice(
                CANDIDATE_SYMBOL,
                70_000L
        );
        CurrentPriceSnapshot positionPrice = currentPrice(
                POSITION_SYMBOL,
                130_000L
        );
        SwingTechnicalAnalysisResult analysis = analysis();
        PortfolioValuationSnapshot valuation = portfolioValuation();
        SwingV1ActionPolicyResult actionResult = buyActionResult();
        SwingV1OrderQuantityResult quantityResult = buyQuantityResult();
        SwingV1DecisionInput input = new SwingV1DecisionInput(
                STRATEGY_IDENTITY,
                history,
                portfolio,
                candidatePrice,
                CurrentPriceLookupSource.PROVIDER,
                List.of(candidatePrice, positionPrice),
                EVALUATED_AT
        );

        when(analysisService.analyze(STRATEGY_IDENTITY, history))
                .thenReturn(analysis);
        when(portfolioValuationService.evaluate(
                portfolio,
                List.of(candidatePrice, positionPrice),
                EVALUATED_AT
        )).thenReturn(valuation);
        when(actionPolicy.decide(analysis, portfolio, candidatePrice))
                .thenReturn(actionResult);
        when(orderQuantityPolicy.calculate(
                actionResult,
                analysis,
                candidatePrice,
                valuation
        )).thenReturn(quantityResult);

        InvestmentDecision decision = decisionService.decide(input);

        assertThat(decision.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(decision.symbol()).isEqualTo(CANDIDATE_SYMBOL);
        assertThat(decision.quantity()).isEqualTo(2L);
        assertThat(decision.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(decision.swingV1Evidence()).isNotNull();
        assertThat(decision.swingV1Evidence().analysis()).isSameAs(analysis);
        assertThat(decision.swingV1Evidence().portfolioValuation())
                .isSameAs(valuation);
        assertThat(decision.swingV1Evidence().currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);

        verify(analysisService).analyze(STRATEGY_IDENTITY, history);
        verify(portfolioValuationService).evaluate(
                portfolio,
                List.of(candidatePrice, positionPrice),
                EVALUATED_AT
        );
        verify(actionPolicy).decide(analysis, portfolio, candidatePrice);
        verify(orderQuantityPolicy).calculate(
                actionResult,
                analysis,
                candidatePrice,
                valuation
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
                                CANDIDATE_SYMBOL,
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
                CANDIDATE_SYMBOL,
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
                CANDIDATE_SYMBOL,
                period,
                new BigDecimal(averagePriceKrw),
                date.minusDays(period - 1L),
                date
        );
    }

    private SwingV1ActionPolicyResult buyActionResult() {
        return new SwingV1ActionPolicyResult(
                InvestmentAction.BUY,
                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                null,
                "Golden cross entry signal detected."
        );
    }

    private SwingV1OrderQuantityResult buyQuantityResult() {
        return new SwingV1OrderQuantityResult(
                InvestmentAction.BUY,
                CANDIDATE_SYMBOL,
                8_250L,
                new BigDecimal("2000.0"),
                4L,
                new OrderQuantityCapacity(
                        InvestmentAction.BUY,
                        CANDIDATE_SYMBOL,
                        70_000L,
                        0L,
                        14L,
                        2L,
                        7L,
                        2L,
                        new BigDecimal("0.042424")
                ),
                2L,
                SwingV1OrderQuantityReasonCode
                        .HARNESS_CAPACITY_LIMITED_BUY,
                "Harness capacity limited the BUY quantity."
        );
    }

    private PortfolioValuationSnapshot portfolioValuation() {
        PortfolioPositionValuation position =
                new PortfolioPositionValuation(
                        POSITION_SYMBOL,
                        5L,
                        120_000L,
                        130_000L,
                        600_000L,
                        650_000L,
                        50_000L,
                        OBSERVED_AT
                );
        return new PortfolioValuationSnapshot(
                EVALUATED_AT,
                1_000_000L,
                650_000L,
                1_650_000L,
                List.of(position)
        );
    }

    private PortfolioSnapshot portfolio() {
        return new PortfolioSnapshot(
                1_000_000L,
                1_600_000L,
                List.of(new PortfolioPosition(
                        POSITION_SYMBOL,
                        5L,
                        120_000L,
                        600_000L
                ))
        );
    }

    private CurrentPriceSnapshot currentPrice(
            String symbol,
            long priceKrw
    ) {
        return new CurrentPriceSnapshot(
                symbol,
                priceKrw,
                OBSERVED_AT
        );
    }
}
