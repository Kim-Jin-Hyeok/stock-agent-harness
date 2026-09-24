package com.stock.broker.order.cancellation.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.cancellation.BrokerOrderCancellationRequest;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;
import com.stock.broker.order.cancellation.provider.BrokerOrderCancellationProvider;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BrokerOrderExpirationCancellationService {
    private static final List<BrokerOrderStatus> CANCELLATION_STATUSES =
            List.of(
                    BrokerOrderStatus.PENDING,
                    BrokerOrderStatus.PARTIALLY_FILLED
            );

    private final BrokerOrderCancellationProvider cancellationProvider;
    private final BrokerOrderRepository orderRepository;
    private final Clock clock;

    public BrokerOrderExpirationCancellationService(
            BrokerOrderCancellationProvider cancellationProvider,
            BrokerOrderRepository orderRepository,
            Clock clock
    ) {
        this.cancellationProvider = Objects.requireNonNull(
                cancellationProvider,
                "cancellationProvider must not be null."
        );
        this.orderRepository = Objects.requireNonNull(
                orderRepository,
                "orderRepository must not be null."
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    public List<BrokerOrderRecord> cancelExpiredOrders() {
        Instant now = clock.instant();
        List<BrokerOrderEntity> targets = Objects.requireNonNull(
                orderRepository
                        .findAllByStatusInAndExpiresAtLessThanEqualAndCancellationStatusIsNullOrderByExpiresAtAsc(
                                CANCELLATION_STATUSES,
                                now
                        ),
                "cancellation targets must not be null."
        );
        List<BrokerOrderRecord> canceledOrders = new ArrayList<>();

        for (BrokerOrderEntity target : targets) {
            BrokerOrderRecord order = target.toRecord();
            BrokerOrderCancellationSubmission submission =
                    Objects.requireNonNull(
                            cancellationProvider.cancelRemaining(
                                    new BrokerOrderCancellationRequest(
                                            order.reference()
                                    )
                            ),
                            "cancellationProvider submission must not be null."
                    );
            BrokerOrderRecord canceledOrder = order.recordCancellation(
                    submission
            );
            BrokerOrderEntity saved = Objects.requireNonNull(
                    orderRepository.save(
                            BrokerOrderEntity.from(canceledOrder)
                    ),
                    "saved order must not be null."
            );
            canceledOrders.add(saved.toRecord());
        }

        return List.copyOf(canceledOrders);
    }
}
