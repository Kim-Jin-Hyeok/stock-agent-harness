package com.stock.broker.order.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderRequest;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.BrokerOrderSubmission;
import com.stock.broker.order.BrokerOrderSubmissionStatus;
import com.stock.broker.order.config.BrokerOrderProperties;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.broker.order.provider.BrokerOrderProvider;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerOrderSubmissionServiceTest {
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-23T00:00:00Z");
    private static final Duration VALIDITY = Duration.ofMinutes(5);

    private final BrokerOrderProvider orderProvider =
            mock(BrokerOrderProvider.class);
    private final BrokerOrderRepository orderRepository =
            mock(BrokerOrderRepository.class);
    private final BrokerOrderSubmissionService service =
            new BrokerOrderSubmissionService(
                    orderProvider,
                    orderRepository,
                    new BrokerOrderProperties(VALIDITY)
            );

    @Test
    void acceptedSubmissionIsStoredAsPendingOrder() {
        BrokerOrderRequest request = request();
        when(orderProvider.submit(request)).thenReturn(acceptedSubmission());
        returnSavedEntity();

        BrokerOrderRecord result = service.submit(
                "run-1",
                strategyIdentity(),
                request
        );

        assertThat(result.reference()).isEqualTo(orderReference());
        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.strategyIdentity()).isEqualTo(strategyIdentity());
        assertThat(result.side()).isEqualTo(BrokerOrderSide.BUY);
        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.requestedQuantity()).isEqualTo(10L);
        assertThat(result.limitPriceKrw()).isEqualTo(70_000L);
        assertThat(result.cumulativeFilledQuantity()).isZero();
        assertThat(result.averageFilledPriceKrw()).isNull();
        assertThat(result.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(result.reason()).isNull();
        assertThat(result.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(result.expiresAt()).isEqualTo(SUBMITTED_AT.plus(VALIDITY));
        assertThat(result.lastReconciledAt()).isNull();

        verify(orderProvider).submit(request);
        verify(orderRepository).save(any(BrokerOrderEntity.class));
    }

    @Test
    void rejectedSubmissionIsStoredAsRejectedOrder() {
        BrokerOrderRequest request = request();
        when(orderProvider.submit(request)).thenReturn(rejectedSubmission());
        returnSavedEntity();

        BrokerOrderRecord result = service.submit(
                "run-1",
                strategyIdentity(),
                request
        );

        assertThat(result.reference()).isNull();
        assertThat(result.status()).isEqualTo(BrokerOrderStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("Broker rejected the order.");
        assertThat(result.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(result.expiresAt()).isNull();
        assertThat(result.lastReconciledAt()).isNull();

        verify(orderProvider).submit(request);
        verify(orderRepository).save(any(BrokerOrderEntity.class));
    }

    @Test
    void providerFailureDoesNotStoreOrder() {
        BrokerOrderRequest request = request();
        RuntimeException failure = new RuntimeException("Broker call failed.");
        when(orderProvider.submit(request)).thenThrow(failure);

        assertThatThrownBy(() -> service.submit(
                "run-1",
                strategyIdentity(),
                request
        )).isSameAs(failure);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void activeOrderPreventsDuplicateProviderSubmission() {
        BrokerOrderRequest request = request();
        when(orderRepository
                .existsByStrategyIdAndStrategyVersionAndHorizonAndSideAndSymbolAndStatusIn(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING,
                        BrokerOrderSide.BUY,
                        "005930",
                        activeOrderStatuses()
                )).thenReturn(true);

        assertThatThrownBy(() -> service.submit(
                "run-2",
                strategyIdentity(),
                request
        )).isInstanceOf(ActiveBrokerOrderExistsException.class)
                .hasMessage(
                        "Active broker order already exists. "
                                + "strategyId=DAY_TRADING_V1, "
                                + "strategyVersion=1, "
                                + "horizon=DAY_TRADING, "
                                + "side=BUY, symbol=005930"
                );

        verifyNoInteractions(orderProvider);
        verify(orderRepository, never()).save(any());
    }

    private void returnSavedEntity() {
        when(orderRepository.save(any(BrokerOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BrokerOrderSubmission acceptedSubmission() {
        return new BrokerOrderSubmission(
                BrokerOrderSubmissionStatus.ACCEPTED,
                orderReference(),
                SUBMITTED_AT,
                null
        );
    }

    private BrokerOrderSubmission rejectedSubmission() {
        return new BrokerOrderSubmission(
                BrokerOrderSubmissionStatus.REJECTED,
                null,
                SUBMITTED_AT,
                "Broker rejected the order."
        );
    }

    private BrokerOrderRequest request() {
        return new BrokerOrderRequest(
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L
        );
    }

    private BrokerOrderReference orderReference() {
        return new BrokerOrderReference("0000123456", "06010");
    }

    private InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(
                "DAY_TRADING_V1",
                1,
                InvestmentHorizon.DAY_TRADING
        );
    }

    private List<BrokerOrderStatus> activeOrderStatuses() {
        return List.of(
                BrokerOrderStatus.PENDING,
                BrokerOrderStatus.PARTIALLY_FILLED
        );
    }
}
