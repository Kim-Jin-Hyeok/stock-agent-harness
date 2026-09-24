package com.stock.broker.order.cancellation.scheduler;

import com.stock.broker.order.cancellation.application.BrokerOrderExpirationCancellationService;
import com.stock.broker.order.cancellation.scheduler.config.BrokerOrderExpirationCancellationSchedulerProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerOrderExpirationCancellationSchedulerTest {

    @Test
    void doesNotCancelExpiredOrdersWhenDisabled() {
        BrokerOrderExpirationCancellationService cancellationService = mock(
                BrokerOrderExpirationCancellationService.class
        );
        BrokerOrderExpirationCancellationScheduler scheduler = scheduler(
                cancellationService,
                false
        );

        scheduler.run();

        verifyNoInteractions(cancellationService);
    }

    @Test
    void cancelsExpiredOrdersWhenEnabled() {
        BrokerOrderExpirationCancellationService cancellationService = mock(
                BrokerOrderExpirationCancellationService.class
        );
        when(cancellationService.cancelExpiredOrders()).thenReturn(List.of());
        BrokerOrderExpirationCancellationScheduler scheduler = scheduler(
                cancellationService,
                true
        );

        scheduler.run();

        verify(cancellationService).cancelExpiredOrders();
    }

    @Test
    void containsCancellationFailureForNextScheduledRun() {
        BrokerOrderExpirationCancellationService cancellationService = mock(
                BrokerOrderExpirationCancellationService.class
        );
        when(cancellationService.cancelExpiredOrders())
                .thenThrow(new IllegalStateException(
                        "KIS cancellation failed."
                ));
        BrokerOrderExpirationCancellationScheduler scheduler = scheduler(
                cancellationService,
                true
        );

        assertThatNoException().isThrownBy(scheduler::run);

        verify(cancellationService).cancelExpiredOrders();
    }

    private BrokerOrderExpirationCancellationScheduler scheduler(
            BrokerOrderExpirationCancellationService cancellationService,
            boolean enabled
    ) {
        return new BrokerOrderExpirationCancellationScheduler(
                cancellationService,
                new BrokerOrderExpirationCancellationSchedulerProperties(
                        enabled,
                        10_000L
                )
        );
    }
}
