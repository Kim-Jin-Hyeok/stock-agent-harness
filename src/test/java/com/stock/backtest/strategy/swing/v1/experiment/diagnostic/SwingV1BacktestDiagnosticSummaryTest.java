package com.stock.backtest.strategy.swing.v1.experiment.diagnostic;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionReasonCode;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingV1BacktestDiagnosticSummaryTest {
    private static final String SYMBOL = "005930";

    @Test
    void countsNoEntryInsufficientHistoryAndMissingNextBarSeparately() {
        SwingV1BacktestStepResult noNextBar = mock(
                SwingV1BacktestStepResult.class
        );
        when(noNextBar.status()).thenReturn(
                SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR
        );

        SwingV1BacktestDiagnosticSummary summary =
                SwingV1BacktestDiagnosticSummary.from(runResult(
                        BacktestPortfolioState.withCash(10_000_000L),
                        decisionStep(
                                SwingV1BacktestStepStatus.HOLD,
                                InvestmentAction.HOLD,
                                InvestmentAction.HOLD,
                                SwingV1ActionReasonCode.NO_ENTRY_SIGNAL,
                                SwingV1OrderQuantityReasonCode.HOLD_NO_ORDER,
                                null
                        ),
                        decisionStep(
                                SwingV1BacktestStepStatus.HOLD,
                                InvestmentAction.HOLD,
                                InvestmentAction.HOLD,
                                SwingV1ActionReasonCode.INSUFFICIENT_DAILY_PRICE_HISTORY,
                                SwingV1OrderQuantityReasonCode.HOLD_NO_ORDER,
                                null
                        ),
                        noNextBar
                ));

        assertThat(summary.symbol()).isEqualTo(SYMBOL);
        assertThat(summary.stepStatusCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        SwingV1BacktestStepStatus.HOLD, 2L,
                        SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR, 1L
                )
        );
        assertThat(summary.actionReasonCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        SwingV1ActionReasonCode.NO_ENTRY_SIGNAL, 1L,
                        SwingV1ActionReasonCode.INSUFFICIENT_DAILY_PRICE_HISTORY, 1L
                )
        );
        assertThat(summary.blockedOrderReasonCounts()).isEmpty();
        assertThat(summary.rejectedTransitionReasonCounts()).isEmpty();
        assertThat(summary.executedBuyCount()).isZero();
        assertThat(summary.executedSellCount()).isZero();
        assertThat(summary.finalPositionQuantity()).isZero();
        assertThatThrownBy(() -> summary.actionReasonCounts().put(
                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                1L
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void separatesQuantityBlockedEntryFromExecutedOpenBuy() {
        SwingV1BacktestDiagnosticSummary summary =
                SwingV1BacktestDiagnosticSummary.from(runResult(
                        new BacktestPortfolioState(
                                8_000_000L,
                                List.of(new BacktestPosition(SYMBOL, 5L, 400_000L))
                        ),
                        decisionStep(
                                SwingV1BacktestStepStatus.HOLD,
                                InvestmentAction.HOLD,
                                InvestmentAction.BUY,
                                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                                SwingV1OrderQuantityReasonCode
                                        .ATR_RISK_BUDGET_INSUFFICIENT,
                                null
                        ),
                        decisionStep(
                                SwingV1BacktestStepStatus.EXECUTED,
                                InvestmentAction.BUY,
                                InvestmentAction.BUY,
                                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                                SwingV1OrderQuantityReasonCode.ATR_RISK_LIMITED_BUY,
                                null
                        )
                ));

        assertThat(summary.stepStatusCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        SwingV1BacktestStepStatus.HOLD, 1L,
                        SwingV1BacktestStepStatus.EXECUTED, 1L
                )
        );
        assertThat(summary.actionReasonCounts()).containsEntry(
                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                2L
        );
        assertThat(summary.blockedOrderReasonCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        SwingV1OrderQuantityReasonCode
                                .ATR_RISK_BUDGET_INSUFFICIENT,
                        1L
                )
        );
        assertThat(summary.executedBuyCount()).isEqualTo(1L);
        assertThat(summary.executedSellCount()).isZero();
        assertThat(summary.finalPositionQuantity()).isEqualTo(5L);
    }

    @Test
    void countsRejectedTransitionAndExecutedSell() {
        SwingV1BacktestDiagnosticSummary summary =
                SwingV1BacktestDiagnosticSummary.from(runResult(
                        BacktestPortfolioState.withCash(10_000_000L),
                        decisionStep(
                                SwingV1BacktestStepStatus.REJECTED,
                                InvestmentAction.BUY,
                                InvestmentAction.BUY,
                                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                                SwingV1OrderQuantityReasonCode.ATR_RISK_LIMITED_BUY,
                                BacktestPortfolioTransitionReasonCode.INSUFFICIENT_CASH
                        ),
                        decisionStep(
                                SwingV1BacktestStepStatus.EXECUTED,
                                InvestmentAction.BUY,
                                InvestmentAction.BUY,
                                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                                SwingV1OrderQuantityReasonCode.ATR_RISK_LIMITED_BUY,
                                null
                        ),
                        decisionStep(
                                SwingV1BacktestStepStatus.EXECUTED,
                                InvestmentAction.SELL,
                                InvestmentAction.SELL,
                                SwingV1ActionReasonCode.ATR_INITIAL_STOP,
                                SwingV1OrderQuantityReasonCode.FULL_POSITION_EXIT,
                                null
                        )
                ));

        assertThat(summary.stepStatusCounts()).containsEntry(
                SwingV1BacktestStepStatus.REJECTED,
                1L
        );
        assertThat(summary.rejectedTransitionReasonCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        BacktestPortfolioTransitionReasonCode.INSUFFICIENT_CASH,
                        1L
                ));
        assertThat(summary.blockedOrderReasonCounts()).isEmpty();
        assertThat(summary.executedBuyCount()).isEqualTo(1L);
        assertThat(summary.executedSellCount()).isEqualTo(1L);
        assertThat(summary.finalPositionQuantity()).isZero();
    }

    @Test
    void rejectsEvaluatedStepWithoutSwingEvidence() {
        SwingV1BacktestStepResult step = mock(
                SwingV1BacktestStepResult.class
        );
        when(step.status()).thenReturn(SwingV1BacktestStepStatus.HOLD);
        when(step.decision()).thenReturn(mock(InvestmentDecision.class));

        assertThatThrownBy(() -> SwingV1BacktestDiagnosticSummary.from(
                runResult(BacktestPortfolioState.withCash(10_000_000L), step)
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("SWING_V1 step decision evidence must not be null.");
    }

    private SwingV1BacktestRunResult runResult(
            BacktestPortfolioState finalState,
            SwingV1BacktestStepResult... steps
    ) {
        SwingV1BacktestRunResult result = mock(
                SwingV1BacktestRunResult.class
        );
        when(result.candidateSymbol()).thenReturn(SYMBOL);
        when(result.steps()).thenReturn(List.of(steps));
        when(result.finalPortfolioState()).thenReturn(finalState);
        return result;
    }

    private SwingV1BacktestStepResult decisionStep(
            SwingV1BacktestStepStatus status,
            InvestmentAction decisionAction,
            InvestmentAction policyAction,
            SwingV1ActionReasonCode actionReasonCode,
            SwingV1OrderQuantityReasonCode quantityReasonCode,
            BacktestPortfolioTransitionReasonCode rejectionReasonCode
    ) {
        SwingV1BacktestStepResult step = mock(
                SwingV1BacktestStepResult.class
        );
        InvestmentDecision decision = mock(InvestmentDecision.class);
        SwingV1DecisionEvidence evidence = mock(
                SwingV1DecisionEvidence.class
        );
        SwingV1ActionPolicyResult actionResult = mock(
                SwingV1ActionPolicyResult.class
        );
        SwingV1OrderQuantityResult quantityResult = mock(
                SwingV1OrderQuantityResult.class
        );
        when(step.status()).thenReturn(status);
        when(step.decision()).thenReturn(decision);
        when(decision.action()).thenReturn(decisionAction);
        when(decision.swingV1Evidence()).thenReturn(evidence);
        when(evidence.actionPolicyResult()).thenReturn(actionResult);
        when(evidence.orderQuantityResult()).thenReturn(quantityResult);
        when(actionResult.action()).thenReturn(policyAction);
        when(actionResult.reasonCode()).thenReturn(actionReasonCode);
        when(quantityResult.reasonCode()).thenReturn(quantityReasonCode);
        if (rejectionReasonCode != null) {
            BacktestPortfolioTransitionResult transition = mock(
                    BacktestPortfolioTransitionResult.class
            );
            when(step.portfolioTransition()).thenReturn(transition);
            when(transition.reasonCode()).thenReturn(rejectionReasonCode);
        }
        return step;
    }
}
