package com.stock.broker.order.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderRequest;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.BrokerOrderSubmission;
import com.stock.broker.order.BrokerOrderSubmissionStatus;
import com.stock.broker.order.config.BrokerOrderProperties;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.broker.order.provider.BrokerOrderProvider;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.time.Instant;
import java.util.Objects;

public class BrokerOrderSubmissionService {
    private final BrokerOrderProvider orderProvider;
    private final BrokerOrderRepository orderRepository;
    private final BrokerOrderProperties properties;

    public BrokerOrderSubmissionService(
            BrokerOrderProvider orderProvider,
            BrokerOrderRepository orderRepository,
            BrokerOrderProperties properties
    ) {
        this.orderProvider = Objects.requireNonNull(
                orderProvider,
                "orderProvider must not be null."
        );
        this.orderRepository = Objects.requireNonNull(
                orderRepository,
                "orderRepository must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null."
        );
    }

    public BrokerOrderRecord submit(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            BrokerOrderRequest request
    ) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank.");
        }
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(request, "request must not be null.");

        BrokerOrderSubmission submission = Objects.requireNonNull(
                orderProvider.submit(request),
                "orderProvider submission must not be null."
        );
        BrokerOrderRecord record = toRecord(
                runId,
                strategyIdentity,
                request,
                submission
        );

        return orderRepository.save(BrokerOrderEntity.from(record)).toRecord();
    }

    private BrokerOrderRecord toRecord(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            BrokerOrderRequest request,
            BrokerOrderSubmission submission
    ) {
        return switch (submission.status()) {
            case ACCEPTED -> {
                Instant expiresAt = submission.submittedAt()
                        .plus(properties.validity());
                yield new BrokerOrderRecord(
                        null,
                        submission.reference(),
                        runId,
                        strategyIdentity,
                        request.side(),
                        request.symbol(),
                        request.quantity(),
                        request.limitPriceKrw(),
                        0L,
                        null,
                        BrokerOrderStatus.PENDING,
                        null,
                        submission.submittedAt(),
                        expiresAt,
                        null
                );
            }
            case REJECTED -> new BrokerOrderRecord(
                    null,
                    null,
                    runId,
                    strategyIdentity,
                    request.side(),
                    request.symbol(),
                    request.quantity(),
                    request.limitPriceKrw(),
                    0L,
                    null,
                    BrokerOrderStatus.REJECTED,
                    submission.reason(),
                    submission.submittedAt(),
                    null,
                    null
            );
        };
    }
}
