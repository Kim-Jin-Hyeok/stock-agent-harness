package com.stock.broker.order.application;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.persistence.BrokerOrderEntity;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.portfolio.PortfolioService;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BrokerOrderPortfolioApplicationService {
    private final BrokerOrderRepository orderRepository;
    private final PortfolioService portfolioService;

    public BrokerOrderPortfolioApplicationService(
            BrokerOrderRepository orderRepository,
            PortfolioService portfolioService
    ) {
        this.orderRepository = Objects.requireNonNull(
                orderRepository,
                "orderRepository must not be null."
        );
        this.portfolioService = Objects.requireNonNull(
                portfolioService,
                "portfolioService must not be null."
        );
    }

    @Transactional
    public List<BrokerOrderRecord> applyUnappliedFills() {
        List<BrokerOrderEntity> targets = Objects.requireNonNull(
                orderRepository.findAllWithUnappliedFills(),
                "unapplied fill targets must not be null."
        );
        List<BrokerOrderRecord> appliedOrders = new ArrayList<>();

        for (BrokerOrderEntity target : targets) {
            BrokerOrderRecord order = target.toRecord();
            applyToPortfolio(order);

            BrokerOrderRecord appliedOrder =
                    order.markCurrentFillAppliedToPortfolio();
            BrokerOrderEntity saved = Objects.requireNonNull(
                    orderRepository.save(BrokerOrderEntity.from(appliedOrder)),
                    "saved order must not be null."
            );
            appliedOrders.add(saved.toRecord());
        }

        return List.copyOf(appliedOrders);
    }

    private void applyToPortfolio(BrokerOrderRecord order) {
        switch (order.side()) {
            case BUY -> portfolioService.applyBuyFill(
                    order.strategyIdentity(),
                    order.symbol(),
                    order.unappliedFilledQuantity(),
                    order.unappliedFilledAmountKrw()
            );
            case SELL -> portfolioService.applySellFill(
                    order.strategyIdentity(),
                    order.symbol(),
                    order.unappliedFilledQuantity(),
                    order.unappliedFilledAmountKrw()
            );
        }
    }
}
