package com.stock.broker.order.cancellation.scheduler;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.cancellation.application.BrokerOrderExpirationCancellationService;
import com.stock.broker.order.cancellation.scheduler.config.BrokerOrderExpirationCancellationSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Objects;

@Slf4j
public class BrokerOrderExpirationCancellationScheduler {
    private final BrokerOrderExpirationCancellationService cancellationService;
    private final BrokerOrderExpirationCancellationSchedulerProperties properties;

    public BrokerOrderExpirationCancellationScheduler(
            BrokerOrderExpirationCancellationService cancellationService,
            BrokerOrderExpirationCancellationSchedulerProperties properties
    ) {
        this.cancellationService = Objects.requireNonNull(
                cancellationService,
                "cancellationService must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null."
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${broker.order.cancellation.scheduler.fixed-delay-ms}"
    )
    public void run() {
        if (!properties.enabled()) {
            log.debug(
                    "Broker order expiration cancellation scheduler is disabled."
            );
            return;
        }

        try {
            List<BrokerOrderRecord> processedOrders =
                    cancellationService.cancelExpiredOrders();
            log.info(
                    "Broker order expiration cancellation completed. "
                            + "processedCount={}",
                    processedOrders.size()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Broker order expiration cancellation failed. "
                            + "failureType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
