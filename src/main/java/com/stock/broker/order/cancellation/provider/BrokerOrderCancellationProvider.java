package com.stock.broker.order.cancellation.provider;

import com.stock.broker.order.cancellation.BrokerOrderCancellationRequest;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;

public interface BrokerOrderCancellationProvider {
    BrokerOrderCancellationSubmission cancelRemaining(
            BrokerOrderCancellationRequest request
    );
}
