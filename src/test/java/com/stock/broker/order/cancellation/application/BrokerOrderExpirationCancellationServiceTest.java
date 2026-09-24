package com.stock.broker.order.cancellation.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.cancellation.BrokerOrderCancellationRequest;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmissionStatus;
import com.stock.broker.order.cancellation.provider.BrokerOrderCancellationProvider;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerOrderExpirationCancellationServiceTest {
    private static final Instant NOW =
            Instant.parse("2026-09-24T01:00:00Z");
    private static final BrokerOrderReference EARLIER_REFERENCE =
            new BrokerOrderReference("0000123456", "06010");
    private static final BrokerOrderReference LATER_REFERENCE =
            new BrokerOrderReference("0000123457", "06010");

    @Test
    void cancelsExpiredOrdersOldestFirstAndSavesResults() {
        Dependencies dependencies = dependencies();
        BrokerOrderRecord earlier = order(
                1L,
                "run-earlier",
                EARLIER_REFERENCE,
                BrokerOrderStatus.PENDING,
                0L,
                null,
                NOW.minusSeconds(600)
        );
        BrokerOrderRecord later = order(
                2L,
                "run-later",
                LATER_REFERENCE,
                BrokerOrderStatus.PARTIALLY_FILLED,
                3L,
                69_900L,
                NOW.minusSeconds(500)
        );
        when(dependencies.clock().instant()).thenReturn(NOW);
        when(dependencies.repository()
                .findAllByStatusInAndExpiresAtLessThanEqualAndCancellationStatusIsNullOrderByExpiresAtAsc(
                        cancellationStatuses(),
                        NOW
                )).thenReturn(List.of(
                        BrokerOrderEntity.from(earlier),
                        BrokerOrderEntity.from(later)
                ));
        BrokerOrderCancellationSubmission accepted = acceptedSubmission();
        BrokerOrderCancellationSubmission rejected = rejectedSubmission();
        when(dependencies.provider().cancelRemaining(
                new BrokerOrderCancellationRequest(EARLIER_REFERENCE)
        )).thenReturn(accepted);
        when(dependencies.provider().cancelRemaining(
                new BrokerOrderCancellationRequest(LATER_REFERENCE)
        )).thenReturn(rejected);
        when(dependencies.repository().save(any(BrokerOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<BrokerOrderRecord> results = dependencies.service()
                .cancelExpiredOrders();

        assertThat(results)
                .extracting(BrokerOrderRecord::runId)
                .containsExactly("run-earlier", "run-later");
        assertThat(results)
                .extracting(BrokerOrderRecord::status)
                .containsExactly(
                        BrokerOrderStatus.PENDING,
                        BrokerOrderStatus.PARTIALLY_FILLED
                );
        assertThat(results)
                .extracting(BrokerOrderRecord::cancellationSubmission)
                .containsExactly(accepted, rejected);

        InOrder inOrder = inOrder(
                dependencies.provider(),
                dependencies.repository()
        );
        inOrder.verify(dependencies.provider()).cancelRemaining(
                new BrokerOrderCancellationRequest(EARLIER_REFERENCE)
        );
        inOrder.verify(dependencies.repository())
                .save(any(BrokerOrderEntity.class));
        inOrder.verify(dependencies.provider()).cancelRemaining(
                new BrokerOrderCancellationRequest(LATER_REFERENCE)
        );
        inOrder.verify(dependencies.repository())
                .save(any(BrokerOrderEntity.class));
    }

    @Test
    void returnsEmptyListWhenThereAreNoExpiredOrders() {
        Dependencies dependencies = dependencies();
        when(dependencies.clock().instant()).thenReturn(NOW);
        when(dependencies.repository()
                .findAllByStatusInAndExpiresAtLessThanEqualAndCancellationStatusIsNullOrderByExpiresAtAsc(
                        cancellationStatuses(),
                        NOW
                )).thenReturn(List.of());

        List<BrokerOrderRecord> results = dependencies.service()
                .cancelExpiredOrders();

        assertThat(results).isEmpty();
        verifyNoInteractions(dependencies.provider());
        verify(dependencies.repository(), never())
                .save(any(BrokerOrderEntity.class));
    }

    @Test
    void doesNotSaveOrderWhenCancellationProviderFails() {
        Dependencies dependencies = dependencies();
        BrokerOrderRecord order = order(
                1L,
                "run-1",
                EARLIER_REFERENCE,
                BrokerOrderStatus.PENDING,
                0L,
                null,
                NOW.minusSeconds(600)
        );
        when(dependencies.clock().instant()).thenReturn(NOW);
        when(dependencies.repository()
                .findAllByStatusInAndExpiresAtLessThanEqualAndCancellationStatusIsNullOrderByExpiresAtAsc(
                        cancellationStatuses(),
                        NOW
                )).thenReturn(List.of(BrokerOrderEntity.from(order)));
        IllegalStateException failure = new IllegalStateException(
                "KIS cancellation failed."
        );
        when(dependencies.provider().cancelRemaining(
                new BrokerOrderCancellationRequest(EARLIER_REFERENCE)
        )).thenThrow(failure);

        assertThatThrownBy(dependencies.service()::cancelExpiredOrders)
                .isSameAs(failure);
        verify(dependencies.repository(), never())
                .save(any(BrokerOrderEntity.class));
    }

    private BrokerOrderRecord order(
            Long id,
            String runId,
            BrokerOrderReference reference,
            BrokerOrderStatus status,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            Instant submittedAt
    ) {
        return new BrokerOrderRecord(
                id,
                reference,
                runId,
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ),
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L,
                cumulativeFilledQuantity,
                averageFilledPriceKrw,
                status,
                null,
                submittedAt,
                submittedAt.plusSeconds(300),
                null
        );
    }

    private BrokerOrderCancellationSubmission acceptedSubmission() {
        return new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                new BrokerOrderReference("0000123458", "06010"),
                NOW,
                null
        );
    }

    private BrokerOrderCancellationSubmission rejectedSubmission() {
        return new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.REJECTED,
                null,
                NOW,
                "The order cannot be canceled."
        );
    }

    private List<BrokerOrderStatus> cancellationStatuses() {
        return List.of(
                BrokerOrderStatus.PENDING,
                BrokerOrderStatus.PARTIALLY_FILLED
        );
    }

    private Dependencies dependencies() {
        BrokerOrderCancellationProvider provider = mock(
                BrokerOrderCancellationProvider.class
        );
        BrokerOrderRepository repository = mock(BrokerOrderRepository.class);
        Clock clock = mock(Clock.class);
        BrokerOrderExpirationCancellationService service =
                new BrokerOrderExpirationCancellationService(
                        provider,
                        repository,
                        clock
                );
        return new Dependencies(provider, repository, clock, service);
    }

    private record Dependencies(
            BrokerOrderCancellationProvider provider,
            BrokerOrderRepository repository,
            Clock clock,
            BrokerOrderExpirationCancellationService service
    ) {
    }
}
