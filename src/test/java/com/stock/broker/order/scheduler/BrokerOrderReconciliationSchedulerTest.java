package com.stock.broker.order.scheduler;

import com.stock.broker.order.application.BrokerOrderPortfolioApplicationService;
import com.stock.broker.order.application.BrokerOrderReconciliationService;
import com.stock.broker.order.scheduler.config.BrokerOrderReconciliationSchedulerProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerOrderReconciliationSchedulerTest {

    @Test
    void doesNotRunOrderProcessingWhenDisabled() {
        BrokerOrderReconciliationService reconciliationService = mock(
                BrokerOrderReconciliationService.class
        );
        BrokerOrderPortfolioApplicationService portfolioApplicationService =
                mock(BrokerOrderPortfolioApplicationService.class);
        BrokerOrderReconciliationScheduler scheduler = scheduler(
                reconciliationService,
                portfolioApplicationService,
                false
        );

        scheduler.run();

        verifyNoInteractions(reconciliationService);
        verifyNoInteractions(portfolioApplicationService);
    }

    @Test
    void reconcilesOrdersThenAppliesUnappliedFillsWhenEnabled() {
        BrokerOrderReconciliationService reconciliationService = mock(
                BrokerOrderReconciliationService.class
        );
        BrokerOrderPortfolioApplicationService portfolioApplicationService =
                mock(BrokerOrderPortfolioApplicationService.class);
        when(reconciliationService.reconcileOpenOrders())
                .thenReturn(List.of());
        when(portfolioApplicationService.applyUnappliedFills())
                .thenReturn(List.of());
        BrokerOrderReconciliationScheduler scheduler = scheduler(
                reconciliationService,
                portfolioApplicationService,
                true
        );

        scheduler.run();

        InOrder inOrder = inOrder(
                reconciliationService,
                portfolioApplicationService
        );
        inOrder.verify(reconciliationService).reconcileOpenOrders();
        inOrder.verify(portfolioApplicationService).applyUnappliedFills();
    }

    @Test
    void appliesPreviousFillsEvenWhenReconciliationFails() {
        BrokerOrderReconciliationService reconciliationService = mock(
                BrokerOrderReconciliationService.class
        );
        BrokerOrderPortfolioApplicationService portfolioApplicationService =
                mock(BrokerOrderPortfolioApplicationService.class);
        when(reconciliationService.reconcileOpenOrders())
                .thenThrow(new IllegalStateException("KIS inquiry failed."));
        when(portfolioApplicationService.applyUnappliedFills())
                .thenReturn(List.of());
        BrokerOrderReconciliationScheduler scheduler = scheduler(
                reconciliationService,
                portfolioApplicationService,
                true
        );

        assertThatNoException().isThrownBy(scheduler::run);

        verify(reconciliationService).reconcileOpenOrders();
        verify(portfolioApplicationService).applyUnappliedFills();
    }

    @Test
    void containsPortfolioApplicationFailureForNextScheduledRun() {
        BrokerOrderReconciliationService reconciliationService = mock(
                BrokerOrderReconciliationService.class
        );
        BrokerOrderPortfolioApplicationService portfolioApplicationService =
                mock(BrokerOrderPortfolioApplicationService.class);
        when(reconciliationService.reconcileOpenOrders())
                .thenReturn(List.of());
        when(portfolioApplicationService.applyUnappliedFills())
                .thenThrow(new IllegalStateException(
                        "Portfolio application failed."
                ));
        BrokerOrderReconciliationScheduler scheduler = scheduler(
                reconciliationService,
                portfolioApplicationService,
                true
        );

        assertThatNoException().isThrownBy(scheduler::run);

        verify(reconciliationService).reconcileOpenOrders();
        verify(portfolioApplicationService).applyUnappliedFills();
    }

    private BrokerOrderReconciliationScheduler scheduler(
            BrokerOrderReconciliationService reconciliationService,
            BrokerOrderPortfolioApplicationService portfolioApplicationService,
            boolean enabled
    ) {
        return new BrokerOrderReconciliationScheduler(
                reconciliationService,
                portfolioApplicationService,
                new BrokerOrderReconciliationSchedulerProperties(
                        enabled,
                        10_000L
                )
        );
    }
}
