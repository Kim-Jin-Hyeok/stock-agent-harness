package com.stock.broker.order.scheduler;

import com.stock.broker.order.application.BrokerOrderReconciliationService;
import com.stock.broker.order.scheduler.config.BrokerOrderReconciliationSchedulerProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerOrderReconciliationSchedulerTest {

    @Test
    void doesNotReconcileOrdersWhenDisabled() {
        BrokerOrderReconciliationService reconciliationService = mock(
                BrokerOrderReconciliationService.class
        );
        BrokerOrderReconciliationScheduler scheduler = scheduler(
                reconciliationService,
                false
        );

        scheduler.run();

        verifyNoInteractions(reconciliationService);
    }

    @Test
    void reconcilesOrdersWhenEnabled() {
        BrokerOrderReconciliationService reconciliationService = mock(
                BrokerOrderReconciliationService.class
        );
        when(reconciliationService.reconcileOpenOrders())
                .thenReturn(List.of());
        BrokerOrderReconciliationScheduler scheduler = scheduler(
                reconciliationService,
                true
        );

        scheduler.run();

        verify(reconciliationService).reconcileOpenOrders();
    }

    @Test
    void containsReconciliationFailureForNextScheduledRun() {
        BrokerOrderReconciliationService reconciliationService = mock(
                BrokerOrderReconciliationService.class
        );
        when(reconciliationService.reconcileOpenOrders())
                .thenThrow(new IllegalStateException("KIS inquiry failed."));
        BrokerOrderReconciliationScheduler scheduler = scheduler(
                reconciliationService,
                true
        );

        assertThatNoException().isThrownBy(scheduler::run);

        verify(reconciliationService).reconcileOpenOrders();
    }

    private BrokerOrderReconciliationScheduler scheduler(
            BrokerOrderReconciliationService reconciliationService,
            boolean enabled
    ) {
        return new BrokerOrderReconciliationScheduler(
                reconciliationService,
                new BrokerOrderReconciliationSchedulerProperties(
                        enabled,
                        10_000L
                )
        );
    }
}
