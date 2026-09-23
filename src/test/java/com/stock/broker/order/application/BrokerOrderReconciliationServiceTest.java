package com.stock.broker.order.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.inquiry.BrokerOrderExecutionSnapshot;
import com.stock.broker.order.inquiry.BrokerOrderInquiryRequest;
import com.stock.broker.order.inquiry.BrokerOrderInquiryResult;
import com.stock.broker.order.inquiry.provider.BrokerOrderInquiryProvider;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerOrderReconciliationServiceTest {
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-23T00:00:00Z");
    private static final Instant OBSERVED_AT =
            SUBMITTED_AT.plusSeconds(30);
    private static final BrokerOrderReference REFERENCE =
            new BrokerOrderReference("0000123456", "06010");

    @Test
    void reconcilesOpenOrdersOldestFirstAndSavesResults() {
        BrokerOrderInquiryProvider inquiryProvider = mock(
                BrokerOrderInquiryProvider.class
        );
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        BrokerOrderRecord earlier = pendingOrder(
                1L,
                "run-earlier",
                SUBMITTED_AT
        );
        BrokerOrderRecord later = pendingOrder(
                2L,
                "run-later",
                SUBMITTED_AT.plusSeconds(10)
        );
        when(orderRepository.findAllByStatusInOrderBySubmittedAtAsc(
                List.of(
                        BrokerOrderStatus.PENDING,
                        BrokerOrderStatus.PARTIALLY_FILLED
                )
        )).thenReturn(List.of(
                BrokerOrderEntity.from(earlier),
                BrokerOrderEntity.from(later)
        ));
        when(inquiryProvider.inquire(request(earlier)))
                .thenReturn(partiallyFilledResult());
        when(inquiryProvider.inquire(request(later)))
                .thenReturn(BrokerOrderInquiryResult.notFound(OBSERVED_AT));
        when(orderRepository.save(any(BrokerOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BrokerOrderReconciliationService service = service(
                inquiryProvider,
                orderRepository
        );

        List<BrokerOrderRecord> results = service.reconcileOpenOrders();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status())
                .isEqualTo(BrokerOrderStatus.PARTIALLY_FILLED);
        assertThat(results.get(0).cumulativeFilledQuantity()).isEqualTo(3L);
        assertThat(results.get(1).status())
                .isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(results)
                .extracting(BrokerOrderRecord::lastReconciledAt)
                .containsExactly(OBSERVED_AT, OBSERVED_AT);

        InOrder inOrder = inOrder(inquiryProvider, orderRepository);
        inOrder.verify(inquiryProvider).inquire(request(earlier));
        inOrder.verify(orderRepository).save(any(BrokerOrderEntity.class));
        inOrder.verify(inquiryProvider).inquire(request(later));
        inOrder.verify(orderRepository).save(any(BrokerOrderEntity.class));
    }

    @Test
    void savesNotFoundObservationWithoutChangingOrderStatus() {
        BrokerOrderInquiryProvider inquiryProvider = mock(
                BrokerOrderInquiryProvider.class
        );
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        BrokerOrderRecord order = pendingOrder(1L, "run-1", SUBMITTED_AT);
        when(orderRepository.findAllByStatusInOrderBySubmittedAtAsc(any()))
                .thenReturn(List.of(BrokerOrderEntity.from(order)));
        when(inquiryProvider.inquire(request(order)))
                .thenReturn(BrokerOrderInquiryResult.notFound(OBSERVED_AT));
        when(orderRepository.save(any(BrokerOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BrokerOrderReconciliationService service = service(
                inquiryProvider,
                orderRepository
        );

        BrokerOrderRecord result = service.reconcileOpenOrders().getFirst();

        assertThat(result.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(result.cumulativeFilledQuantity()).isZero();
        assertThat(result.lastReconciledAt()).isEqualTo(OBSERVED_AT);
        ArgumentCaptor<BrokerOrderEntity> captor =
                ArgumentCaptor.forClass(BrokerOrderEntity.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().toRecord().id()).isEqualTo(1L);
    }

    @Test
    void doesNotSaveOrderWhenProviderFails() {
        BrokerOrderInquiryProvider inquiryProvider = mock(
                BrokerOrderInquiryProvider.class
        );
        BrokerOrderRepository orderRepository = mock(
                BrokerOrderRepository.class
        );
        BrokerOrderRecord order = pendingOrder(1L, "run-1", SUBMITTED_AT);
        when(orderRepository.findAllByStatusInOrderBySubmittedAtAsc(any()))
                .thenReturn(List.of(BrokerOrderEntity.from(order)));
        IllegalStateException failure = new IllegalStateException(
                "KIS inquiry failed."
        );
        when(inquiryProvider.inquire(request(order))).thenThrow(failure);
        BrokerOrderReconciliationService service = service(
                inquiryProvider,
                orderRepository
        );

        assertThatThrownBy(service::reconcileOpenOrders).isSameAs(failure);
        verify(orderRepository, never()).save(any(BrokerOrderEntity.class));
    }

    private BrokerOrderReconciliationService service(
            BrokerOrderInquiryProvider inquiryProvider,
            BrokerOrderRepository orderRepository
    ) {
        return new BrokerOrderReconciliationService(
                inquiryProvider,
                orderRepository
        );
    }

    private BrokerOrderRecord pendingOrder(
            Long id,
            String runId,
            Instant submittedAt
    ) {
        return new BrokerOrderRecord(
                id,
                REFERENCE,
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
                0L,
                null,
                BrokerOrderStatus.PENDING,
                null,
                submittedAt,
                submittedAt.plusSeconds(300),
                null
        );
    }

    private BrokerOrderInquiryRequest request(BrokerOrderRecord order) {
        return new BrokerOrderInquiryRequest(
                order.reference(),
                order.symbol(),
                order.submittedAt()
        );
    }

    private BrokerOrderInquiryResult partiallyFilledResult() {
        return BrokerOrderInquiryResult.found(
                new BrokerOrderExecutionSnapshot(
                        REFERENCE,
                        10L,
                        3L,
                        69_900L,
                        BrokerOrderStatus.PARTIALLY_FILLED,
                        null
                ),
                OBSERVED_AT
        );
    }
}
