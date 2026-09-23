package com.stock.broker.order.provider;

import com.stock.broker.order.BrokerOrderRequest;
import com.stock.broker.order.BrokerOrderSubmission;

public interface BrokerOrderProvider {
    BrokerOrderSubmission submit(BrokerOrderRequest request);
}
