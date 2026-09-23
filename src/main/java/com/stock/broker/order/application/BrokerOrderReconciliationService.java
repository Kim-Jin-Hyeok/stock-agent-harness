package com.stock.broker.order.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.inquiry.BrokerOrderInquiryRequest;
import com.stock.broker.order.inquiry.BrokerOrderInquiryResult;
import com.stock.broker.order.inquiry.provider.BrokerOrderInquiryProvider;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BrokerOrderReconciliationService {
    private static final List<BrokerOrderStatus> RECONCILIATION_STATUSES =
            List.of(
                    BrokerOrderStatus.PENDING,
                    BrokerOrderStatus.PARTIALLY_FILLED
            );

    private final BrokerOrderInquiryProvider inquiryProvider;
    private final BrokerOrderRepository orderRepository;

    public BrokerOrderReconciliationService(
            BrokerOrderInquiryProvider inquiryProvider,
            BrokerOrderRepository orderRepository
    ) {
        this.inquiryProvider = Objects.requireNonNull(
                inquiryProvider,
                "inquiryProvider must not be null."
        );
        this.orderRepository = Objects.requireNonNull(
                orderRepository,
                "orderRepository must not be null."
        );
    }

    public List<BrokerOrderRecord> reconcileOpenOrders() {
        List<BrokerOrderEntity> targets =
                orderRepository.findAllByStatusInOrderBySubmittedAtAsc(
                        RECONCILIATION_STATUSES
                );
        List<BrokerOrderRecord> reconciledOrders = new ArrayList<>();

        for (BrokerOrderEntity target : targets) {
            BrokerOrderRecord order = target.toRecord();
            BrokerOrderInquiryResult result = Objects.requireNonNull(
                    inquiryProvider.inquire(new BrokerOrderInquiryRequest(
                            order.reference(),
                            order.symbol(),
                            order.submittedAt()
                    )),
                    "inquiryProvider result must not be null."
            );
            BrokerOrderRecord reconciledOrder = order.reconcile(result);
            BrokerOrderEntity saved = Objects.requireNonNull(
                    orderRepository.save(
                            BrokerOrderEntity.from(reconciledOrder)
                    ),
                    "saved order must not be null."
            );
            reconciledOrders.add(saved.toRecord());
        }

        return List.copyOf(reconciledOrders);
    }
}
