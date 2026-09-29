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
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class SwingV1BacktestStepService {
    private final BacktestPortfolioEvaluationContextFactory contextFactory;
    private final SwingV1DecisionService decisionService;
    private final DailyOpenFillApproximationService fillService;
    private final BacktestPortfolioTransitionService transitionService;

    public SwingV1BacktestStepService(
            BacktestPortfolioEvaluationContextFactory contextFactory,
            SwingV1DecisionService decisionService,
            DailyOpenFillApproximationService fillService,
            BacktestPortfolioTransitionService transitionService
    ) {
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

        BacktestPortfolioEvaluationContext context = contextFactory.create(
                request.portfolioState(),
                request.decisionDate(),
                request.evaluatedAt(),
                request.evaluationBarsBySymbol()
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
                        CurrentPriceLookupSource.BACKTEST_DAILY_CLOSE,
                        context.currentPrices(),
                        request.evaluatedAt()
                )
        );

        if (decision.action() == InvestmentAction.HOLD) {
            return SwingV1BacktestStepResult.hold(
                    request.decisionDate(),
                    decision,
                    request.portfolioState()
            );
        }
        validateOrderDecision(decision, request.candidateSymbol());

        Optional<DailyOpenFillApproximation> fill =
                fillService.approximate(
                        decision.symbol(),
                        request.decisionDate(),
                        decision.action(),
                        decision.quantity(),
                        request.costModel()
                );
        if (fill.isEmpty()) {
            return SwingV1BacktestStepResult.noNextDailyBar(
                    request.decisionDate(),
                    decision,
                    request.portfolioState()
            );
        }

        DailyOpenFillApproximation filledOrder = fill.orElseThrow();
        BacktestPortfolioTransitionResult transition =
                transitionService.apply(
                        request.portfolioState(),
                        filledOrder
                );
        return SwingV1BacktestStepResult.transitioned(
                request.decisionDate(),
                decision,
                filledOrder,
                transition,
                request.portfolioState()
        );
    }

    private void validateOrderDecision(
            InvestmentDecision decision,
            String candidateSymbol
    ) {
        if (decision.action() == null
                || decision.action() == InvestmentAction.HOLD
                || decision.symbol() == null
                || decision.symbol().isBlank()
                || !decision.symbol().equals(candidateSymbol)
                || decision.quantity() == null
                || decision.quantity() <= 0
                || decision.expectedPriceKrw() == null
                || decision.expectedPriceKrw() <= 0) {
            throw new IllegalStateException(
                    "SWING_V1 decision must contain a valid candidate order."
            );
        }
    }
}
