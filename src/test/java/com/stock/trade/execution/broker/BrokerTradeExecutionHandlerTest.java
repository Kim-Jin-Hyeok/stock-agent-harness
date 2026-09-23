package com.stock.trade.execution.broker;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderRequest;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerTradeExecutionHandlerTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-23T00:00:00Z");

    private final BrokerOrderSubmissionService submissionService =
            mock(BrokerOrderSubmissionService.class);
    private final BrokerTradeExecutionHandler handler =
            new BrokerTradeExecutionHandler(submissionService);

    @Test
    void acceptedBuyOrderIsReturnedAsSubmitted() {
        InvestmentDecision decision = decision(InvestmentAction.BUY);
        BrokerOrderRequest request = request(BrokerOrderSide.BUY);
        when(submissionService.submit(
                "run-1",
                STRATEGY_IDENTITY,
                request
        )).thenReturn(pendingOrder(BrokerOrderSide.BUY));

        TradeResult result = handler.execute(
                "run-1",
                STRATEGY_IDENTITY,
                decision
        );

        assertThat(result.status()).isEqualTo(TradeStatus.SUBMITTED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.ORDER_SUBMITTED);
        assertThat(result.action()).isEqualTo(InvestmentAction.BUY);
        verify(submissionService).submit(
                "run-1",
                STRATEGY_IDENTITY,
                request
        );
    }

    @Test
    void rejectedSellOrderIsReturnedAsRejected() {
        InvestmentDecision decision = decision(InvestmentAction.SELL);
        BrokerOrderRequest request = request(BrokerOrderSide.SELL);
        when(submissionService.submit(
                "run-1",
                STRATEGY_IDENTITY,
                request
        )).thenReturn(rejectedOrder(BrokerOrderSide.SELL));

        TradeResult result = handler.execute(
                "run-1",
                STRATEGY_IDENTITY,
                decision
        );

        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.reasonCode())
                .isEqualTo(TradeReasonCode.BROKER_ORDER_REJECTED);
        assertThat(result.reason()).isEqualTo("Broker rejected the order.");
        verify(submissionService).submit(
                "run-1",
                STRATEGY_IDENTITY,
                request
        );
    }

    private BrokerOrderRecord pendingOrder(BrokerOrderSide side) {
        return order(
                new BrokerOrderReference("0000123456", "06010"),
                side,
                BrokerOrderStatus.PENDING,
                null,
                SUBMITTED_AT.plusSeconds(300)
        );
    }

    private BrokerOrderRecord rejectedOrder(BrokerOrderSide side) {
        return order(
                null,
                side,
                BrokerOrderStatus.REJECTED,
                "Broker rejected the order.",
                null
        );
    }

    private BrokerOrderRecord order(
            BrokerOrderReference reference,
            BrokerOrderSide side,
            BrokerOrderStatus status,
            String reason,
            Instant expiresAt
    ) {
        return new BrokerOrderRecord(
                1L,
                reference,
                "run-1",
                STRATEGY_IDENTITY,
                side,
                "005930",
                10L,
                70_000L,
                0L,
                null,
                status,
                reason,
                SUBMITTED_AT,
                expiresAt,
                null
        );
    }

    private BrokerOrderRequest request(BrokerOrderSide side) {
        return new BrokerOrderRequest(
                side,
                "005930",
                10L,
                70_000L
        );
    }

    private InvestmentDecision decision(InvestmentAction action) {
        return new InvestmentDecision(
                action,
                "005930",
                10L,
                70_000L,
                "Test broker trade decision."
        );
    }
}
