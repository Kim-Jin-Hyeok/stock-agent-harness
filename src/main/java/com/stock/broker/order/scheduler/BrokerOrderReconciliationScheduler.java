package com.stock.broker.order.scheduler;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.application.BrokerOrderPortfolioApplicationService;
import com.stock.broker.order.application.BrokerOrderReconciliationService;
import com.stock.broker.order.scheduler.config.BrokerOrderReconciliationSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Objects;

@Slf4j
public class BrokerOrderReconciliationScheduler {
    private final BrokerOrderReconciliationService reconciliationService;
    private final BrokerOrderPortfolioApplicationService portfolioApplicationService;
    private final BrokerOrderReconciliationSchedulerProperties properties;

    public BrokerOrderReconciliationScheduler(
            BrokerOrderReconciliationService reconciliationService,
            BrokerOrderPortfolioApplicationService portfolioApplicationService,
            BrokerOrderReconciliationSchedulerProperties properties
    ) {
        this.reconciliationService = Objects.requireNonNull(
                reconciliationService,
                "reconciliationService must not be null."
        );
        this.portfolioApplicationService = Objects.requireNonNull(
                portfolioApplicationService,
                "portfolioApplicationService must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null."
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${broker.order.reconciliation.scheduler.fixed-delay-ms}"
    )
    public void run() {
        if (!properties.enabled()) {
            log.debug("Broker order reconciliation scheduler is disabled.");
            return;
        }

        reconcileOrders();
        applyUnappliedFills();
    }

    private void reconcileOrders() {
        try {
            List<BrokerOrderRecord> reconciledOrders =
                    reconciliationService.reconcileOpenOrders();
            log.info(
                    "Broker order reconciliation completed. reconciledCount={}",
                    reconciledOrders.size()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Broker order reconciliation failed. failureType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void applyUnappliedFills() {
        try {
            List<BrokerOrderRecord> appliedOrders =
                    portfolioApplicationService.applyUnappliedFills();
            log.info(
                    "Broker order portfolio application completed. "
                            + "appliedCount={}",
                    appliedOrders.size()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Broker order portfolio application failed. "
                            + "failureType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
