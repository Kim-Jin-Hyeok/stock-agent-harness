package com.stock.broker.order.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.portfolio.PortfolioService;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerOrderPortfolioApplicationServiceTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void appliesUnappliedBuyFillAndSavesAppliedState() {
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        PortfolioService portfolioService = mock(PortfolioService.class);
        BrokerOrderRecord order = partiallyFilledOrder(BrokerOrderSide.BUY);
        when(orderRepository.findAllWithUnappliedFills()).thenReturn(
                List.of(BrokerOrderEntity.from(order))
        );
        when(orderRepository.save(any(BrokerOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BrokerOrderPortfolioApplicationService service = service(
                orderRepository,
                portfolioService
        );

        List<BrokerOrderRecord> results = service.applyUnappliedFills();

        verify(portfolioService).applyBuyFill(
                STRATEGY_IDENTITY,
                "005930",
                2L,
                139_000L
        );
        ArgumentCaptor<BrokerOrderEntity> captor =
                ArgumentCaptor.forClass(BrokerOrderEntity.class);
        verify(orderRepository).save(captor.capture());
        BrokerOrderRecord saved = captor.getValue().toRecord();
        assertThat(saved.portfolioAppliedQuantity()).isEqualTo(5L);
        assertThat(saved.portfolioAppliedAmountKrw()).isEqualTo(349_000L);
        assertThat(results).containsExactly(saved);
    }

    @Test
    void appliesUnappliedSellFill() {
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        PortfolioService portfolioService = mock(PortfolioService.class);
        BrokerOrderRecord order = partiallyFilledOrder(BrokerOrderSide.SELL);
        when(orderRepository.findAllWithUnappliedFills()).thenReturn(
                List.of(BrokerOrderEntity.from(order))
        );
        when(orderRepository.save(any(BrokerOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BrokerOrderPortfolioApplicationService service = service(
                orderRepository,
                portfolioService
        );

        service.applyUnappliedFills();

        verify(portfolioService).applySellFill(
                STRATEGY_IDENTITY,
                "005930",
                2L,
                139_000L
        );
    }

    @Test
    void appliesUnappliedFillFromCanceledOrder() {
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        PortfolioService portfolioService = mock(PortfolioService.class);
        BrokerOrderRecord order = order(
                BrokerOrderSide.BUY,
                BrokerOrderStatus.CANCELED,
                3L,
                209_700L,
                0L,
                0L
        );
        when(orderRepository.findAllWithUnappliedFills()).thenReturn(
                List.of(BrokerOrderEntity.from(order))
        );
        when(orderRepository.save(any(BrokerOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BrokerOrderPortfolioApplicationService service = service(
                orderRepository,
                portfolioService
        );

        service.applyUnappliedFills();

        verify(portfolioService).applyBuyFill(
                STRATEGY_IDENTITY,
                "005930",
                3L,
                209_700L
        );
        verify(orderRepository).save(any(BrokerOrderEntity.class));
    }

    @Test
    void doesNothingWhenThereAreNoUnappliedFills() {
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        PortfolioService portfolioService = mock(PortfolioService.class);
        when(orderRepository.findAllWithUnappliedFills())
                .thenReturn(List.of());
        BrokerOrderPortfolioApplicationService service = service(
                orderRepository,
                portfolioService
        );

        List<BrokerOrderRecord> results = service.applyUnappliedFills();

        assertThat(results).isEmpty();
        verifyNoInteractions(portfolioService);
        verify(orderRepository, never()).save(any(BrokerOrderEntity.class));
    }

    @Test
    void doesNotSaveAppliedStateWhenPortfolioApplicationFails() {
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        PortfolioService portfolioService = mock(PortfolioService.class);
        BrokerOrderRecord order = partiallyFilledOrder(BrokerOrderSide.BUY);
        when(orderRepository.findAllWithUnappliedFills()).thenReturn(
                List.of(BrokerOrderEntity.from(order))
        );
        IllegalStateException failure = new IllegalStateException(
                "Portfolio application failed."
        );
        doThrow(failure).when(portfolioService).applyBuyFill(
                STRATEGY_IDENTITY,
                "005930",
                2L,
                139_000L
        );
        BrokerOrderPortfolioApplicationService service = service(
                orderRepository,
                portfolioService
        );

        assertThatThrownBy(service::applyUnappliedFills).isSameAs(failure);
        verify(orderRepository, never()).save(any(BrokerOrderEntity.class));
    }

    private BrokerOrderPortfolioApplicationService service(
            BrokerOrderRepository orderRepository,
            PortfolioService portfolioService
    ) {
        return new BrokerOrderPortfolioApplicationService(
                orderRepository,
                portfolioService
        );
    }

    private BrokerOrderRecord partiallyFilledOrder(BrokerOrderSide side) {
        return order(
                side,
                BrokerOrderStatus.PARTIALLY_FILLED,
                5L,
                349_000L,
                3L,
                210_000L
        );
    }

    private BrokerOrderRecord order(
            BrokerOrderSide side,
            BrokerOrderStatus status,
            long cumulativeFilledQuantity,
            long cumulativeFilledAmountKrw,
            long portfolioAppliedQuantity,
            long portfolioAppliedAmountKrw
    ) {
        return new BrokerOrderRecord(
                1L,
                new BrokerOrderReference("0000123456", "06010"),
                "run-1",
                STRATEGY_IDENTITY,
                side,
                "005930",
                10L,
                70_000L,
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw,
                portfolioAppliedQuantity,
                portfolioAppliedAmountKrw,
                cumulativeFilledAmountKrw / cumulativeFilledQuantity,
                status,
                null,
                SUBMITTED_AT,
                SUBMITTED_AT.plusSeconds(300),
                SUBMITTED_AT.plusSeconds(30),
                null
        );
    }
}
