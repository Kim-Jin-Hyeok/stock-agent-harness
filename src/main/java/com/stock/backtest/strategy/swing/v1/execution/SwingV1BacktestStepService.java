package com.stock.backtest.strategy.swing.v1.execution;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.SwingV1DecisionInput;
import com.stock.agent.decision.swing.v1.SwingV1DecisionService;
import com.stock.backtest.context.portfolio.BacktestPortfolioEvaluationContext;
import com.stock.backtest.context.portfolio.BacktestPortfolioEvaluationContextFactory;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximationService;
import com.stock.backtest.portfolio.transition.BacktestPortfolioTransitionService;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class SwingV1BacktestStepService {
    private static final LocalTime DECISION_TIME = LocalTime.of(9, 10);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyPriceHistoryQueryService priceHistoryQueryService;
    private final BacktestPortfolioEvaluationContextFactory contextFactory;
    private final SwingV1DecisionService decisionService;
    private final DailyOpenFillApproximationService fillService;
    private final BacktestPortfolioTransitionService transitionService;

    public SwingV1BacktestStepService(
            DailyPriceHistoryQueryService priceHistoryQueryService,
            BacktestPortfolioEvaluationContextFactory contextFactory,
            SwingV1DecisionService decisionService,
            DailyOpenFillApproximationService fillService,
            BacktestPortfolioTransitionService transitionService
    ) {
        this.priceHistoryQueryService = Objects.requireNonNull(
                priceHistoryQueryService,
                "priceHistoryQueryService must not be null."
        );
        this.contextFactory = Objects.requireNonNull(
                contextFactory,
                "contextFactory must not be null."
        );
        this.decisionService = Objects.requireNonNull(
                decisionService,
                "decisionService must not be null."
        );
        this.fillService = Objects.requireNonNull(
                fillService,
                "fillService must not be null."
        );
        this.transitionService = Objects.requireNonNull(
                transitionService,
                "transitionService must not be null."
        );
    }

    public SwingV1BacktestStepResult execute(
            SwingV1BacktestStepRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        Optional<DailyPriceBar> nextDailyPriceBar = priceHistoryQueryService
                .getFirstDailyPriceBarAfter(
                        request.candidateSymbol(),
                        request.signalDate()
                );
        if (nextDailyPriceBar.isEmpty()) {
            return SwingV1BacktestStepResult.noNextDailyBar(
                    request.signalDate(),
                    request.candidateSymbol(),
                    request.portfolioState()
            );
        }

        DailyPriceBar decisionBar = nextDailyPriceBar.orElseThrow();
        Instant evaluatedAt = decisionBar.tradingDate()
                .atTime(DECISION_TIME)
                .atZone(MARKET_ZONE)
                .toInstant();
        BacktestPortfolioEvaluationContext context =
                contextFactory.createAtOpen(
                        request.portfolioState(),
                        decisionBar.tradingDate(),
                        evaluatedAt,
                        Map.of(request.candidateSymbol(), decisionBar)
                );
        CurrentPriceSnapshot candidateCurrentPrice = context
                .findCurrentPrice(request.candidateSymbol())
                .orElseThrow(() -> new IllegalStateException(
                        "Candidate current price must exist in context."
                ));
        InvestmentDecision decision = decisionService.decide(
                new SwingV1DecisionInput(
                        request.strategyIdentity(),
                        request.dailyPriceHistory(),
                        context.portfolioSnapshot(),
                        candidateCurrentPrice,
                        CurrentPriceLookupSource.BACKTEST_DAILY_OPEN,
                        context.currentPrices(),
                        evaluatedAt
                )
        );

        if (decision.action() == InvestmentAction.HOLD) {
            return SwingV1BacktestStepResult.hold(
                    request.signalDate(),
                    decisionBar.tradingDate(),
                    decision,
                    request.portfolioState()
            );
        }
        validateOrderDecision(
                decision,
                request.candidateSymbol(),
                candidateCurrentPrice.priceKrw()
        );

        DailyOpenFillApproximation fill = fillService.approximate(
                decision.symbol(),
                request.signalDate(),
                decisionBar,
                decision.action(),
                decision.quantity(),
                request.costModel()
        );

        BacktestPortfolioTransitionResult transition =
                transitionService.apply(
                        request.portfolioState(),
                        fill
                );
        return SwingV1BacktestStepResult.transitioned(
                request.signalDate(),
                decisionBar.tradingDate(),
                decision,
                fill,
                transition,
                request.portfolioState()
        );
    }

    private void validateOrderDecision(
            InvestmentDecision decision,
            String candidateSymbol,
            long candidateCurrentPriceKrw
    ) {
        if (decision.action() == null
                || decision.action() == InvestmentAction.HOLD
                || decision.symbol() == null
                || decision.symbol().isBlank()
                || !decision.symbol().equals(candidateSymbol)
                || decision.quantity() == null
                || decision.quantity() <= 0
                || decision.expectedPriceKrw() == null
                || decision.expectedPriceKrw() <= 0
                || decision.expectedPriceKrw() != candidateCurrentPriceKrw) {
            throw new IllegalStateException(
                    "SWING_V1 decision must contain a valid candidate order."
            );
        }
    }
}
