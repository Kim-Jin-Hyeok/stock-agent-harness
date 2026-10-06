package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.execution.TradeExecutionHandler;
import com.stock.trade.execution.broker.BrokerTradeExecutionHandler;
import com.stock.trade.execution.config.TradeExecutionMode;
import com.stock.trade.execution.config.TradeExecutionProperties;
import com.stock.trade.execution.virtual.VirtualTradeExecutionHandler;
import com.stock.trade.persistence.TradeRecordEntity;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static com.stock.portfolio.support.PortfolioSnapshotStoreFixture.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeExecutorTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final TradeExecutionHandler tradeExecutionHandler =
            mock(TradeExecutionHandler.class);
    private final TradeRecordRepository tradeRecordRepository =
            mock(TradeRecordRepository.class);
    private final TradeHistoryService tradeHistoryService =
            new TradeHistoryService(tradeRecordRepository);
    private final TradeExecutor tradeExecutor = new TradeExecutor(
            tradeExecutionHandler,
            tradeHistoryService,
            new TradeExecutionProperties(TradeExecutionMode.VIRTUAL, true)
    );

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void riskDeniedDecisionIsRejectedWithoutExecution(boolean ordersEnabled) {
        InvestmentDecision decision = orderDecision(InvestmentAction.BUY);

        TradeResult result = executor(
                tradeExecutionHandler,
                TradeExecutionMode.VIRTUAL,
                ordersEnabled
        ).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                deniedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.RISK_DENIED);
        verifyNoInteractions(tradeExecutionHandler);
        verify(tradeRecordRepository).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void holdDecisionIsSkippedWithoutExecution(boolean ordersEnabled) {
        InvestmentDecision decision = holdDecision();

        TradeResult result = executor(
                tradeExecutionHandler,
                TradeExecutionMode.VIRTUAL,
                ordersEnabled
        ).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.SKIPPED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.HOLD_NO_ORDER);
        verifyNoInteractions(tradeExecutionHandler);
        verify(tradeRecordRepository).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = InvestmentAction.class, names = {"BUY", "SELL"})
    void approvedOrderDecisionIsDelegatedAndRecorded(InvestmentAction action) {
        InvestmentDecision decision = orderDecision(action);
        TradeResult expected = executedResult(decision);
        when(tradeExecutionHandler.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision
        )).thenReturn(expected);

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result).isSameAs(expected);
        verify(tradeExecutionHandler).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision
        );
        verify(tradeRecordRepository).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = InvestmentAction.class, names = {"BUY", "SELL"})
    void disabledOrderIsRejectedAndRecordedWithoutExecution(InvestmentAction action) {
        InvestmentDecision decision = orderDecision(action);

        TradeResult result = executor(
                tradeExecutionHandler,
                TradeExecutionMode.VIRTUAL,
                false
        ).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.reasonCode())
                .isEqualTo(TradeReasonCode.ORDER_EXECUTION_DISABLED);
        assertThat(result.reason())
                .isEqualTo("Order execution is disabled by configuration.");
        assertThat(result.action()).isEqualTo(action);
        assertThat(result.symbol()).isEqualTo(decision.symbol());
        assertThat(result.quantity()).isEqualTo(decision.quantity());
        assertThat(result.expectedPriceKrw()).isEqualTo(decision.expectedPriceKrw());
        assertThat(result.estimatedOrderAmountKrw())
                .isEqualTo(decision.estimatedOrderAmountKrw());
        verifyNoInteractions(tradeExecutionHandler);

        ArgumentCaptor<TradeRecordEntity> captor =
                ArgumentCaptor.forClass(TradeRecordEntity.class);
        verify(tradeRecordRepository).save(captor.capture());
        TradeRecord record = captor.getValue().toRecord();
        assertThat(record.runId()).isEqualTo("abc");
        assertThat(record.status()).isEqualTo(result.status());
        assertThat(record.reasonCode()).isEqualTo(result.reasonCode());
        assertThat(record.reason()).isEqualTo(result.reason());
        assertThat(record.action()).isEqualTo(result.action());
        assertThat(record.symbol()).isEqualTo(result.symbol());
        assertThat(record.quantity()).isEqualTo(result.quantity());
        assertThat(record.priceKrw()).isEqualTo(result.expectedPriceKrw());
        assertThat(record.orderAmountKrw())
                .isEqualTo(result.estimatedOrderAmountKrw());
    }

    @ParameterizedTest
    @EnumSource(value = InvestmentAction.class, names = {"BUY", "SELL"})
    void disabledVirtualOrderLeavesPortfolioUnchanged(InvestmentAction action) {
        PortfolioService portfolioService = new PortfolioService(create());
        portfolioService.applyBuy(STRATEGY_IDENTITY, "TEST", 15L, 50_000L);
        PortfolioSnapshot before = portfolioService.getCurrentSnapshot(
                STRATEGY_IDENTITY
        );
        InvestmentDecision decision = orderDecision(action);

        TradeResult result = executor(
                new VirtualTradeExecutionHandler(portfolioService),
                TradeExecutionMode.VIRTUAL,
                false
        ).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.reasonCode())
                .isEqualTo(TradeReasonCode.ORDER_EXECUTION_DISABLED);
        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY))
                .isEqualTo(before);
        verify(tradeRecordRepository).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = InvestmentAction.class, names = {"BUY", "SELL"})
    void disabledBrokerOrderDoesNotReachSubmissionService(InvestmentAction action) {
        BrokerOrderSubmissionService submissionService =
                mock(BrokerOrderSubmissionService.class);
        InvestmentDecision decision = orderDecision(action);

        TradeResult result = executor(
                new BrokerTradeExecutionHandler(submissionService),
                TradeExecutionMode.BROKER,
                false
        ).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.reasonCode())
                .isEqualTo(TradeReasonCode.ORDER_EXECUTION_DISABLED);
        verifyNoInteractions(submissionService);
        verify(tradeRecordRepository).save(any());
    }

    @Test
    void disabledOrderStillDoesNotExecuteWhenHistoryRecordingFails() {
        InvestmentDecision decision = orderDecision(InvestmentAction.BUY);
        IllegalStateException failure = new IllegalStateException("History save failed.");
        when(tradeRecordRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> executor(
                tradeExecutionHandler,
                TradeExecutionMode.VIRTUAL,
                false
        ).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        )).isSameAs(failure);

        verifyNoInteractions(tradeExecutionHandler);
    }

    private TradeExecutor executor(
            TradeExecutionHandler handler,
            TradeExecutionMode mode,
            boolean ordersEnabled
    ) {
        return new TradeExecutor(
                handler,
                tradeHistoryService,
                new TradeExecutionProperties(mode, ordersEnabled)
        );
    }

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Test HOLD decision."
        );
    }

    private InvestmentDecision orderDecision(InvestmentAction action) {
        return new InvestmentDecision(
                action,
                "TEST",
                10L,
                100_000L,
                "Test order decision."
        );
    }

    private TradeResult executedResult(InvestmentDecision decision) {
        return new TradeResult(
                TradeStatus.EXECUTED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                TradeReasonCode.EXECUTION_COMPLETED,
                decision.action() + " execution is complete."
        );
    }

    private RiskCheckResult approvedRiskCheckResult(
            InvestmentDecision decision
    ) {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                RiskReasonCode.RISK_APPROVED,
                "Test risk approved."
        );
    }

    private RiskCheckResult deniedRiskCheckResult(
            InvestmentDecision decision
    ) {
        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED,
                "Test risk denied."
        );
    }
}
